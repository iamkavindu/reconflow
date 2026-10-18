package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionStatus;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationEvaluatorTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final Instant TIME = Instant.parse("2026-10-10T10:00:00Z");
    private final ReconciliationEvaluator evaluator = new ReconciliationEvaluator();

    @Test
    void explainsAllSixSatisfiedRulesAndComparesAmountsNumerically() {
        var results =
                evaluator.evaluate(transaction(), provider(TENANT, "100.0000", "USD", TIME), Duration.ofMinutes(10));

        assertThat(results).hasSize(6).allMatch(result -> result.satisfied());
        assertThat(results)
                .extracting(result -> result.rule())
                .containsExactly("REFERENCE", "AMOUNT", "CURRENCY", "TYPE", "STATUS", "TIME_DIFFERENCE");
    }

    @Test
    void exposesFailedAmountCurrencyAndTimeRules() {
        var results = evaluator.evaluate(
                transaction(), provider(TENANT, "96.80", "EUR", TIME.plusSeconds(601)), Duration.ofMinutes(10));

        assertThat(results.stream().filter(result -> !result.satisfied()))
                .extracting(result -> result.rule())
                .containsExactly("AMOUNT", "CURRENCY", "TIME_DIFFERENCE");
    }

    @Test
    void refusesCrossTenantComparison() {
        assertThatThrownBy(() -> evaluator.evaluate(
                        transaction(),
                        provider(new TenantId(UUID.randomUUID()), "100.00", "USD", TIME),
                        Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reconciliation records must belong to the same tenant");
    }

    private static TransactionView transaction() {
        return new TransactionView(
                new TransactionId(UUID.randomUUID()),
                TENANT,
                "ORD-1",
                TransactionType.PAYMENT,
                new BigDecimal("100.00"),
                "USD",
                TIME,
                TransactionStatus.RECORDED,
                TIME);
    }

    private static ProviderTransactionView provider(
            TenantId tenant, String amount, String currency, Instant occurredAt) {
        return new ProviderTransactionView(
                new ProviderTransactionId(UUID.randomUUID()),
                tenant,
                "evt-1",
                "stripe",
                "pi-1",
                "ORD-1",
                ProviderTransactionType.PAYMENT,
                new BigDecimal(amount),
                currency,
                ProviderTransactionStatus.SUCCEEDED,
                occurredAt,
                TIME);
    }
}
