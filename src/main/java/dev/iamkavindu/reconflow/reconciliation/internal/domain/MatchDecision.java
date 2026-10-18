package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record MatchDecision(
        TenantId tenantId,
        ProviderTransactionId providerTransactionId,
        Instant matchedAt,
        List<RuleEvaluation> evaluations) {

    public MatchDecision {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(providerTransactionId, "providerTransactionId must not be null");
        Objects.requireNonNull(matchedAt, "matchedAt must not be null");
        evaluations = List.copyOf(evaluations);
        if (evaluations.isEmpty() || evaluations.stream().anyMatch(evaluation -> !evaluation.satisfied())) {
            throw new IllegalArgumentException("a match requires satisfied rule evaluations");
        }
    }
}
