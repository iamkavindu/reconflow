package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import dev.iamkavindu.reconflow.reconciliation.Discrepancy;
import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DiscrepancyDetector {

    public List<Discrepancy> detect(List<RuleEvaluation> evaluations, TransactionType type, Instant detectedAt) {
        return evaluations.stream()
                .filter(evaluation -> !evaluation.satisfied())
                .map(evaluation -> new Discrepancy(
                        UUID.randomUUID(),
                        classify(evaluation.rule(), type),
                        "Reconciliation rule failed: " + evaluation.rule(),
                        evaluation.expected(),
                        evaluation.actual(),
                        detectedAt))
                .toList();
    }

    private static DiscrepancyType classify(String rule, TransactionType type) {
        return switch (rule) {
            case "AMOUNT" -> type == TransactionType.REFUND
                    ? DiscrepancyType.REFUND_MISMATCH
                    : DiscrepancyType.AMOUNT_MISMATCH;
            case "CURRENCY" -> DiscrepancyType.CURRENCY_MISMATCH;
            case "TYPE" -> DiscrepancyType.REFUND_MISMATCH;
            case "STATUS" -> DiscrepancyType.STATUS_MISMATCH;
            case "TIME_DIFFERENCE" -> DiscrepancyType.TIME_WINDOW_MISMATCH;
            default -> throw new IllegalArgumentException("unsupported failed reconciliation rule: " + rule);
        };
    }
}
