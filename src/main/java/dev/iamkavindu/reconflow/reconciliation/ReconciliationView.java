package dev.iamkavindu.reconflow.reconciliation;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;
import java.util.List;

public record ReconciliationView(
        ReconciliationId reconciliationId,
        TenantId tenantId,
        TransactionId transactionId,
        ReconciliationStatus status,
        Instant startedAt,
        Instant expiresAt,
        ProviderTransactionId providerTransactionId,
        Instant matchedAt,
        List<RuleEvaluation> ruleEvaluations,
        Instant decidedAt,
        List<ProviderTransactionId> comparedProviderTransactionIds,
        List<Discrepancy> discrepancies,
        String businessReference,
        ProviderTransactionId providerOriginId) {

    public ReconciliationView {
        ruleEvaluations = List.copyOf(ruleEvaluations);
        comparedProviderTransactionIds = List.copyOf(comparedProviderTransactionIds);
        discrepancies = List.copyOf(discrepancies);
    }
}
