package dev.iamkavindu.reconflow.reconciliation.internal.persistence;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.MatchDecision;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.MatchingWindow;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.util.Arrays;
import tools.jackson.databind.json.JsonMapper;

final class ReconciliationPersistenceMapper {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ReconciliationPersistenceMapper() {}

    static ReconciliationJpaEntity toEntity(Reconciliation reconciliation) {
        return new ReconciliationJpaEntity(
                reconciliation.reconciliationId().value(),
                reconciliation.tenantId().value(),
                reconciliation.transactionId().value(),
                reconciliation.status(),
                reconciliation.matchingWindow().startedAt(),
                reconciliation.matchingWindow().expiresAt(),
                reconciliation.matchDecision() == null
                        ? null
                        : reconciliation.matchDecision().providerTransactionId().value(),
                reconciliation.matchDecision() == null ? null : reconciliation.matchDecision().matchedAt(),
                reconciliation.matchDecision() == null
                        ? null
                        : JSON.writeValueAsString(reconciliation.matchDecision().evaluations()));
    }

    static Reconciliation toDomain(ReconciliationJpaEntity entity) {
        return Reconciliation.rehydrate(
                new ReconciliationId(entity.id()),
                new TenantId(entity.tenantId()),
                new TransactionId(entity.transactionId()),
                entity.status(),
                new MatchingWindow(entity.startedAt(), entity.expiresAt()),
                entity.providerTransactionId() == null
                        ? null
                        : new MatchDecision(
                                new TenantId(entity.tenantId()),
                                new ProviderTransactionId(entity.providerTransactionId()),
                                entity.matchedAt(),
                                Arrays.asList(JSON.readValue(entity.ruleEvaluations(), RuleEvaluation[].class))));
    }
}
