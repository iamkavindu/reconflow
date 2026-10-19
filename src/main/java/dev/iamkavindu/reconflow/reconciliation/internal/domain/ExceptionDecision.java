package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.Discrepancy;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ExceptionDecision(
        TenantId tenantId,
        List<ProviderTransactionId> providerTransactionIds,
        Instant decidedAt,
        List<RuleEvaluation> evaluations,
        List<Discrepancy> discrepancies) {

    public ExceptionDecision {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        providerTransactionIds = List.copyOf(providerTransactionIds);
        evaluations = List.copyOf(evaluations);
        discrepancies = List.copyOf(discrepancies);
        if (discrepancies.isEmpty()) {
            throw new IllegalArgumentException("an exception decision requires at least one discrepancy");
        }
    }
}
