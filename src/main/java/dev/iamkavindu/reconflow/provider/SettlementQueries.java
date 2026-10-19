package dev.iamkavindu.reconflow.provider;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;

public interface SettlementQueries {

    SettlementView getSettlement(TenantId tenantId, SettlementId settlementId);

    List<SettlementView> forProviderTransaction(TenantId tenantId, ProviderTransactionId providerTransactionId);
}
