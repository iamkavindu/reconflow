package dev.iamkavindu.reconflow.reconciliation.internal.persistence;

import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationRepository;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaReconciliationRepository implements ReconciliationRepository {

    private final SpringDataReconciliationRepository repository;

    JpaReconciliationRepository(SpringDataReconciliationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Reconciliation save(Reconciliation reconciliation) {
        ReconciliationJpaEntity saved = repository.save(ReconciliationPersistenceMapper.toEntity(reconciliation));
        return ReconciliationPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Reconciliation> findByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId) {
        return repository
                .findByTenantIdAndTransactionId(tenantId.value(), transactionId.value())
                .map(ReconciliationPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId) {
        return repository.existsByTenantIdAndTransactionId(tenantId.value(), transactionId.value());
    }
}
