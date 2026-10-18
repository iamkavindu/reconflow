package dev.iamkavindu.reconflow.reconciliation.events;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationMatched(
        UUID eventId,
        TenantId tenantId,
        ReconciliationId reconciliationId,
        TransactionId transactionId,
        ProviderTransactionId providerTransactionId,
        Instant matchedAt,
        List<RuleEvaluation> ruleEvaluations,
        UUID correlationId,
        UUID causationId) {

    public ReconciliationMatched {
        ruleEvaluations = List.copyOf(ruleEvaluations);
    }
}
