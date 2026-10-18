package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscrepancyDetectorTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private final DiscrepancyDetector detector = new DiscrepancyDetector();

    @Test
    void classifiesFailedRulesWithoutReportingSatisfiedRules() {
        var discrepancies = detector.detect(
                List.of(
                        new RuleEvaluation("AMOUNT", false, "100", "90"),
                        new RuleEvaluation("CURRENCY", true, "USD", "USD"),
                        new RuleEvaluation("STATUS", false, "SUCCEEDED", "FAILED"),
                        new RuleEvaluation("TIME_DIFFERENCE", false, "PT10M", "PT20M")),
                TransactionType.PAYMENT,
                NOW);

        assertThat(discrepancies)
                .extracting(discrepancy -> discrepancy.type())
                .containsExactly(
                        DiscrepancyType.AMOUNT_MISMATCH,
                        DiscrepancyType.STATUS_MISMATCH,
                        DiscrepancyType.TIME_WINDOW_MISMATCH);
    }

    @Test
    void reviewRequiresDiscrepanciesAndPreservesTenantAndLifecycleBoundaries() {
        var tenant = new TenantId(UUID.randomUUID());
        var pending = Reconciliation.pending(
                new ReconciliationId(UUID.randomUUID()),
                tenant,
                new TransactionId(UUID.randomUUID()),
                new MatchingWindow(NOW.minusSeconds(1), NOW.plusSeconds(600)));
        var discrepancies = detector.detect(
                List.of(new RuleEvaluation("AMOUNT", false, "100", "90")), TransactionType.PAYMENT, NOW);
        var decision = new ExceptionDecision(tenant, List.of(), NOW, List.of(), discrepancies);

        var review = pending.requireReview(decision);

        assertThat(review.exceptionDecision()).isEqualTo(decision);
        assertThatThrownBy(() -> review.requireReview(decision)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ExceptionDecision(tenant, List.of(), NOW, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.requireReview(new ExceptionDecision(
                        new TenantId(UUID.randomUUID()), List.of(), NOW, List.of(), discrepancies)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
