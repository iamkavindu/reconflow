package dev.iamkavindu.reconflow.provider;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;

public interface ProviderTransactionCandidates {

    List<ProviderTransactionView> findByBusinessReference(TenantId tenantId, String businessReference);
}
