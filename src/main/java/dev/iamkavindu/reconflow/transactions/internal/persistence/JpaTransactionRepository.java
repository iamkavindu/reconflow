package dev.iamkavindu.reconflow.transactions.internal.persistence;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.internal.domain.Transaction;
import dev.iamkavindu.reconflow.transactions.internal.domain.TransactionRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaTransactionRepository implements TransactionRepository {

    private final SpringDataTransactionRepository repository;

    JpaTransactionRepository(SpringDataTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionJpaEntity saved = repository.save(TransactionPersistenceMapper.toEntity(transaction));
        return TransactionPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Transaction> findByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId) {
        return repository
                .findByTenantIdAndId(tenantId.value(), transactionId.value())
                .map(TransactionPersistenceMapper::toDomain);
    }
}
