package dev.iamkavindu.reconflow.transactions.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentTenant;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.tenancy.UserRole;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionStatus;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import dev.iamkavindu.reconflow.transactions.events.TransactionRecorded;
import dev.iamkavindu.reconflow.transactions.internal.domain.Transaction;
import dev.iamkavindu.reconflow.transactions.internal.domain.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionApplicationServiceTest {

    private static final TenantId TENANT_ID = new TenantId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final TenantId OTHER_TENANT_ID =
            new TenantId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-10T09:25:00Z");
    private static final Instant CLOCK_INSTANT = Instant.parse("2026-10-10T09:25:03.123456789Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-10-10T09:25:03.123456Z");

    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final CurrentTenant currentTenant = () -> TENANT_ID;
    private final List<Object> publishedEvents = new ArrayList<>();
    private final TransactionApplicationService service = new TransactionApplicationService(
            currentTenant,
            () -> new Actor(TENANT_ID, "analyst", UserRole.ANALYST),
            repository,
            Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC),
            publishedEvents::add);

    @Test
    void recordsTransactionUsingTrustedTenantAndApplicationControlledValues() {
        TransactionView recorded = service.recordTransaction(new RecordTransactionCommand(
                "ORD-1001",
                TransactionType.PAYMENT,
                new BigDecimal("100.00"),
                Currency.getInstance("USD"),
                OCCURRED_AT));

        assertThat(recorded.transactionId()).isNotNull();
        assertThat(recorded.tenantId()).isEqualTo(TENANT_ID);
        assertThat(recorded.businessReference()).isEqualTo("ORD-1001");
        assertThat(recorded.type()).isEqualTo(TransactionType.PAYMENT);
        assertThat(recorded.amount()).isEqualByComparingTo("100.00");
        assertThat(recorded.currency()).isEqualTo("USD");
        assertThat(recorded.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(recorded.status()).isEqualTo(TransactionStatus.RECORDED);
        assertThat(recorded.recordedAt()).isEqualTo(RECORDED_AT);
        assertThat(publishedEvents).singleElement().isInstanceOf(TransactionRecorded.class);
        assertThat(((TransactionRecorded) publishedEvents.getFirst()).recordedBy())
                .isEqualTo("analyst");
    }

    @Test
    void retrievesTransactionOnlyWithinRequestedTenant() {
        TransactionView recorded = service.recordTransaction(new RecordTransactionCommand(
                "ORD-1001",
                TransactionType.PAYMENT,
                new BigDecimal("100.00"),
                Currency.getInstance("USD"),
                OCCURRED_AT));

        assertThat(service.getTransaction(TENANT_ID, recorded.transactionId())).isEqualTo(recorded);
        assertThatThrownBy(() -> service.getTransaction(OTHER_TENANT_ID, recorded.transactionId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    private static final class InMemoryTransactionRepository implements TransactionRepository {

        private final Map<TransactionId, Transaction> transactions = new HashMap<>();

        @Override
        public Transaction save(Transaction transaction) {
            transactions.put(transaction.transactionId(), transaction);
            return transaction;
        }

        @Override
        public Optional<Transaction> findByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId) {
            return Optional.ofNullable(transactions.get(transactionId))
                    .filter(transaction -> transaction.tenantId().equals(tenantId));
        }
    }
}
