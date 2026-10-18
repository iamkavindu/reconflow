package dev.iamkavindu.reconflow.reconciliation.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationRepository;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.events.TransactionRecorded;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionRecordedListenerTest {

    @Test
    void createsOnlyOnePendingReconciliationWhenEventIsDeliveredAgain() {
        InMemoryReconciliationRepository repository = new InMemoryReconciliationRepository();
        TransactionRecordedListener listener = new TransactionRecordedListener(repository, new ReconciliationPolicy());
        Instant recordedAt = Instant.parse("2026-10-10T09:25:00Z");
        TransactionRecorded event = new TransactionRecorded(
                UUID.randomUUID(),
                new TenantId(UUID.randomUUID()),
                new TransactionId(UUID.randomUUID()),
                "ORD-1001",
                recordedAt,
                UUID.randomUUID());

        listener.on(event);
        listener.on(event);

        assertThat(repository.reconciliations).singleElement().satisfies(reconciliation -> {
            assertThat(reconciliation.status().name()).isEqualTo("PENDING");
            assertThat(reconciliation.matchingWindow().startedAt()).isEqualTo(recordedAt);
            assertThat(reconciliation.matchingWindow().expiresAt()).isEqualTo(recordedAt.plusSeconds(600));
        });
    }

    private static final class InMemoryReconciliationRepository implements ReconciliationRepository {

        private final List<Reconciliation> reconciliations = new ArrayList<>();

        @Override
        public Reconciliation save(Reconciliation reconciliation) {
            reconciliations.add(reconciliation);
            return reconciliation;
        }

        @Override
        public Optional<Reconciliation> findByTenantIdAndTransactionId(
                TenantId tenantId, TransactionId transactionId) {
            return reconciliations.stream()
                    .filter(reconciliation -> reconciliation.tenantId().equals(tenantId))
                    .filter(reconciliation -> reconciliation.transactionId().equals(transactionId))
                    .findFirst();
        }

        @Override
        public boolean existsByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId) {
            return findByTenantIdAndTransactionId(tenantId, transactionId).isPresent();
        }
    }
}
