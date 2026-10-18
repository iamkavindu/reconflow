package dev.iamkavindu.reconflow.provider;

import dev.iamkavindu.reconflow.tenancy.TenantId;

public interface ProviderTransactionQueries {

    ProviderTransactionView getProviderTransaction(TenantId tenantId, ProviderTransactionId providerTransactionId);
}
