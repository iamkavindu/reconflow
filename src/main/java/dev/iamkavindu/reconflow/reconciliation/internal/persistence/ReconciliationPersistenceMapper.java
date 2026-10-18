package dev.iamkavindu.reconflow.reconciliation.internal.persistence;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.MatchingWindow;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;

final class ReconciliationPersistenceMapper {

    private ReconciliationPersistenceMapper() {}

    static ReconciliationJpaEntity toEntity(Reconciliation reconciliation) {
        return new ReconciliationJpaEntity(
                reconciliation.reconciliationId().value(),
                reconciliation.tenantId().value(),
                reconciliation.transactionId().value(),
                reconciliation.status(),
                reconciliation.matchingWindow().startedAt(),
                reconciliation.matchingWindow().expiresAt());
    }

    static Reconciliation toDomain(ReconciliationJpaEntity entity) {
        return Reconciliation.rehydrate(
                new ReconciliationId(entity.id()),
                new TenantId(entity.tenantId()),
                new TransactionId(entity.transactionId()),
                entity.status(),
                new MatchingWindow(entity.startedAt(), entity.expiresAt()));
    }
}
