package dev.iamkavindu.reconflow.reconciliation.internal.persistence;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationRepository;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;
import java.util.List;
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

    @Override
    public Optional<Reconciliation> findByTenantIdAndId(TenantId tenantId, ReconciliationId reconciliationId) {
        return repository.findByTenantIdAndId(tenantId.value(), reconciliationId.value())
                .map(ReconciliationPersistenceMapper::toDomain);
    }

    @Override
    public List<Reconciliation> findByTenantIdAndProviderTransactionId(TenantId tenantId, ProviderTransactionId providerId) {
        String providerLink = "[{\"value\":\"" + providerId.value() + "\"}]";
        return repository.findProviderLinks(tenantId.value(), providerId.value(), providerLink).stream()
                .map(ReconciliationPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Reconciliation> findPendingExpiredBefore(Instant now, int limit) {
        return repository.findExpired(now, limit).stream()
                .map(ReconciliationPersistenceMapper::toDomain)
                .toList();
    }
}
