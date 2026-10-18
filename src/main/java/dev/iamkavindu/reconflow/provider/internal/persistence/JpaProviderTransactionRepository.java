package dev.iamkavindu.reconflow.provider.internal.persistence;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderTransaction;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderTransactionRepository;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaProviderTransactionRepository implements ProviderTransactionRepository {

    private final SpringDataProviderTransactionRepository repository;

    JpaProviderTransactionRepository(SpringDataProviderTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public SaveResult saveIfAbsent(ProviderTransaction providerTransaction) {
        int inserted = repository.insertIfAbsent(
                providerTransaction.providerTransactionId().value(),
                providerTransaction.tenantId().value(),
                providerTransaction.providerEventId().value(),
                providerTransaction.provider().value(),
                providerTransaction.providerReference().value(),
                providerTransaction.businessReference().value(),
                providerTransaction.type().name(),
                providerTransaction.money().amount(),
                providerTransaction.money().currency().getCurrencyCode(),
                providerTransaction.status().name(),
                providerTransaction.occurredAt(),
                providerTransaction.recordedAt(),
                providerTransaction.correlationId(),
                providerTransaction.causationId());

        if (inserted == 1) {
            return new SaveResult(providerTransaction, true);
        }

        ProviderTransaction existing = repository
                .findByTenantIdAndProviderAndProviderEventId(
                        providerTransaction.tenantId().value(),
                        providerTransaction.provider().value(),
                        providerTransaction.providerEventId().value())
                .or(() -> repository.findByTenantIdAndProviderAndProviderTransactionReference(
                        providerTransaction.tenantId().value(),
                        providerTransaction.provider().value(),
                        providerTransaction.providerReference().value()))
                .map(ProviderTransactionPersistenceMapper::toDomain)
                .orElseThrow(() -> new NoSuchElementException("Conflicting provider transaction was not found"));

        return new SaveResult(existing, false);
    }

    @Override
    public Optional<ProviderTransaction> findByTenantIdAndProviderTransactionId(
            TenantId tenantId, ProviderTransactionId providerTransactionId) {
        return repository
                .findByTenantIdAndId(tenantId.value(), providerTransactionId.value())
                .map(ProviderTransactionPersistenceMapper::toDomain);
    }
}
