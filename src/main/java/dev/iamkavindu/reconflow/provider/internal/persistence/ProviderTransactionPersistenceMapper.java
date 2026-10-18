package dev.iamkavindu.reconflow.provider.internal.persistence;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderBusinessReference;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderCode;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderEventId;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderMoney;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderReference;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderTransaction;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.Currency;

final class ProviderTransactionPersistenceMapper {

    private ProviderTransactionPersistenceMapper() {}

    static ProviderTransaction toDomain(ProviderTransactionJpaEntity entity) {
        return ProviderTransaction.record(
                new ProviderTransactionId(entity.id()),
                new TenantId(entity.tenantId()),
                new ProviderEventId(entity.providerEventId()),
                new ProviderCode(entity.provider()),
                new ProviderReference(entity.providerTransactionReference()),
                new ProviderBusinessReference(entity.businessReference()),
                entity.type(),
                new ProviderMoney(entity.amount(), Currency.getInstance(entity.currency())),
                entity.status(),
                entity.occurredAt(),
                entity.recordedAt(),
                entity.correlationId(),
                entity.causationId());
    }
}
