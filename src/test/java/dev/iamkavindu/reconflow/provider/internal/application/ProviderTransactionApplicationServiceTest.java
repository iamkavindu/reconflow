package dev.iamkavindu.reconflow.provider.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.provider.events.ProviderTransactionRecorded;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderTransaction;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderTransactionRepository;
import dev.iamkavindu.reconflow.tenancy.CurrentTenant;
import dev.iamkavindu.reconflow.tenancy.TenantId;
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

class ProviderTransactionApplicationServiceTest {

    private static final TenantId TENANT_ID = new TenantId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final TenantId OTHER_TENANT_ID =
            new TenantId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final Instant RECORDED_AT = Instant.parse("2026-10-10T09:25:03Z");

    private final InMemoryProviderTransactionRepository repository = new InMemoryProviderTransactionRepository();
    private final CurrentTenant currentTenant = () -> TENANT_ID;
    private final List<Object> publishedEvents = new ArrayList<>();
    private final ProviderTransactionApplicationService service = new ProviderTransactionApplicationService(
            currentTenant, repository, Clock.fixed(RECORDED_AT, ZoneOffset.UTC), publishedEvents::add);

    @Test
    void ingestsDuplicateDeliveryExactlyOnce() {
        IngestProviderEventCommand command = command("evt-1001", "pi-1001");

        ProviderTransactionView first = service.ingestProviderEvent(command);
        ProviderTransactionView duplicate = service.ingestProviderEvent(command);

        assertThat(duplicate).isEqualTo(first);
        assertThat(repository.transactions).hasSize(1);
        assertThat(publishedEvents).singleElement().isInstanceOf(ProviderTransactionRecorded.class);
    }

    @Test
    void retrievesProviderTransactionOnlyWithinRequestedTenant() {
        ProviderTransactionView recorded = service.ingestProviderEvent(command("evt-1002", "pi-1002"));

        assertThat(service.getProviderTransaction(TENANT_ID, recorded.providerTransactionId()))
                .isEqualTo(recorded);
        assertThatThrownBy(() -> service.getProviderTransaction(OTHER_TENANT_ID, recorded.providerTransactionId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    private static IngestProviderEventCommand command(String eventId, String providerReference) {
        return new IngestProviderEventCommand(
                eventId,
                "stripe",
                providerReference,
                "ORD-1001",
                ProviderTransactionType.PAYMENT,
                new BigDecimal("100.00"),
                Currency.getInstance("USD"),
                ProviderTransactionStatus.SUCCEEDED,
                Instant.parse("2026-10-10T09:25:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private static final class InMemoryProviderTransactionRepository implements ProviderTransactionRepository {

        private final Map<String, ProviderTransaction> transactions = new HashMap<>();

        @Override
        public SaveResult saveIfAbsent(ProviderTransaction providerTransaction) {
            String key = key(providerTransaction);
            ProviderTransaction existing = transactions.putIfAbsent(key, providerTransaction);
            return existing == null
                    ? new SaveResult(providerTransaction, true)
                    : new SaveResult(existing, false);
        }

        @Override
        public Optional<ProviderTransaction> findByTenantIdAndProviderTransactionId(
                TenantId tenantId, ProviderTransactionId providerTransactionId) {
            return transactions.values().stream()
                    .filter(transaction -> transaction.tenantId().equals(tenantId))
                    .filter(transaction -> transaction.providerTransactionId().equals(providerTransactionId))
                    .findFirst();
        }

        private static String key(ProviderTransaction transaction) {
            return transaction.tenantId().value()
                    + ":"
                    + transaction.provider().value()
                    + ":"
                    + transaction.providerEventId().value();
        }
    }
}
