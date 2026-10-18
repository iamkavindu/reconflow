package dev.iamkavindu.reconflow.transactions;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;

public interface TransactionCandidates {

    List<TransactionView> findByBusinessReference(TenantId tenantId, String businessReference);
}
