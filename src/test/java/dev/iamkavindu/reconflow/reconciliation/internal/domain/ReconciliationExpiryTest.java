package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.Discrepancy;
import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationExpiryTest {

    private static final Instant START = Instant.parse("2026-10-10T10:00:00Z");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    @Test
    void timelyArrivalCanBeProcessedAfterDeadline() {
        var pending = Reconciliation.pending(
                new ReconciliationId(UUID.randomUUID()),
                TENANT,
                new TransactionId(UUID.randomUUID()),
                new MatchingWindow(START, START.plusSeconds(600)),
                "ORD-1");
        var match = new MatchDecision(
                TENANT,
                new ProviderTransactionId(UUID.randomUUID()),
                START.plusSeconds(601),
                List.of(new RuleEvaluation("AMOUNT", true, "100", "100")));

        assertThat(pending.match(match, START.plusSeconds(30)).status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThatThrownBy(() -> pending.match(match, START.plusSeconds(600)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiryCannotHappenEarlyOrBeRepeated() {
        var pending = Reconciliation.pendingProvider(
                new ReconciliationId(UUID.randomUUID()),
                TENANT,
                new ProviderTransactionId(UUID.randomUUID()),
                new MatchingWindow(START, START.plusSeconds(600)),
                "ORD-2");
        var discrepancy = new Discrepancy(
                UUID.randomUUID(),
                DiscrepancyType.MISSING_INTERNAL_TRANSACTION,
                "No internal record",
                "internal record",
                "none",
                START.plusSeconds(600));
        var decision = new ExceptionDecision(
                TENANT, List.of(pending.providerOriginId()), START.plusSeconds(600), List.of(), List.of(discrepancy));

        var unmatched = pending.expire(decision);

        assertThat(unmatched.status()).isEqualTo(ReconciliationStatus.UNMATCHED);
        assertThatThrownBy(() -> unmatched.expire(decision)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.expire(new ExceptionDecision(
                        TENANT,
                        List.of(pending.providerOriginId()),
                        START.plusSeconds(599),
                        List.of(),
                        List.of(discrepancy))))
                .isInstanceOf(IllegalStateException.class);
    }
}
