package dev.iamkavindu.reconflow.provider.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderTransactionTest {

    @Test
    void recordsNormalizedProviderFinancialFact() {
        ProviderTransactionId transactionId = new ProviderTransactionId(UUID.randomUUID());
        TenantId tenantId = new TenantId(UUID.randomUUID());
        Instant occurredAt = Instant.parse("2026-10-10T09:25:00Z");
        Instant recordedAt = Instant.parse("2026-10-10T09:25:03Z");

        ProviderTransaction transaction = ProviderTransaction.record(
                transactionId,
                tenantId,
                new ProviderEventId("evt-1001"),
                new ProviderCode("Stripe"),
                new ProviderReference("pi-1001"),
                new ProviderBusinessReference("ORD-1001"),
                ProviderTransactionType.PAYMENT,
                new ProviderMoney(new BigDecimal("100.00"), Currency.getInstance("USD")),
                ProviderTransactionStatus.SUCCEEDED,
                occurredAt,
                recordedAt,
                UUID.randomUUID(),
                UUID.randomUUID());

        assertThat(transaction.providerTransactionId()).isEqualTo(transactionId);
        assertThat(transaction.tenantId()).isEqualTo(tenantId);
        assertThat(transaction.provider().value()).isEqualTo("stripe");
        assertThat(transaction.providerEventId().value()).isEqualTo("evt-1001");
        assertThat(transaction.providerReference().value()).isEqualTo("pi-1001");
        assertThat(transaction.businessReference().value()).isEqualTo("ORD-1001");
        assertThat(transaction.status()).isEqualTo(ProviderTransactionStatus.SUCCEEDED);
    }
}
