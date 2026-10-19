package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationTest {

    @Test
    void permitsOnlyOneMatchAndRejectsLateOrCrossTenantDecisions() {
        Instant start = Instant.parse("2026-10-10T09:25:00Z");
        TenantId tenant = new TenantId(UUID.randomUUID());
        var pending = Reconciliation.pending(
                new ReconciliationId(UUID.randomUUID()),
                tenant,
                new TransactionId(UUID.randomUUID()),
                new MatchingWindow(start, start.plusSeconds(600)),
                "ORD-1");
        var decision = decision(tenant, start.plusSeconds(10));

        var matched = pending.match(decision);

        assertThat(matched.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(matched.matchDecision()).isEqualTo(decision);
        assertThatThrownBy(() -> matched.match(decision)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.match(decision(tenant, start.plusSeconds(600))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.match(decision(new TenantId(UUID.randomUUID()), start.plusSeconds(10))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFailedOrEmptyRuleEvidence() {
        var tenant = new TenantId(UUID.randomUUID());
        var providerId = new ProviderTransactionId(UUID.randomUUID());
        assertThatThrownBy(() -> new MatchDecision(tenant, providerId, Instant.now(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchDecision(
                        tenant, providerId, Instant.now(), List.of(new RuleEvaluation("AMOUNT", false, "100", "90"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MatchDecision decision(TenantId tenant, Instant matchedAt) {
        return new MatchDecision(
                tenant,
                new ProviderTransactionId(UUID.randomUUID()),
                matchedAt,
                List.of(new RuleEvaluation("AMOUNT", true, "100", "100")));
    }

    @Test
    void startsPendingForTheRequestedTransactionAndWindow() {
        ReconciliationId reconciliationId = new ReconciliationId(UUID.randomUUID());
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TransactionId transactionId = new TransactionId(UUID.randomUUID());
        MatchingWindow window =
                new MatchingWindow(Instant.parse("2026-10-10T09:25:00Z"), Instant.parse("2026-10-10T09:35:00Z"));

        Reconciliation reconciliation =
                Reconciliation.pending(reconciliationId, tenantId, transactionId, window, "ORD-1");

        assertThat(reconciliation.reconciliationId()).isEqualTo(reconciliationId);
        assertThat(reconciliation.tenantId()).isEqualTo(tenantId);
        assertThat(reconciliation.transactionId()).isEqualTo(transactionId);
        assertThat(reconciliation.status()).isEqualTo(ReconciliationStatus.PENDING);
        assertThat(reconciliation.matchingWindow()).isEqualTo(window);
    }
}
