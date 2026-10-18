package dev.iamkavindu.reconflow.provider.internal.domain;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.Optional;

public interface ProviderTransactionRepository {

    SaveResult saveIfAbsent(ProviderTransaction providerTransaction);

    Optional<ProviderTransaction> findByTenantIdAndProviderTransactionId(
            TenantId tenantId, ProviderTransactionId providerTransactionId);

    record SaveResult(ProviderTransaction providerTransaction, boolean created) {}
}
