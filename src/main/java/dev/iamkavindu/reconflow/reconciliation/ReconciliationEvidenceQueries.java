package dev.iamkavindu.reconflow.reconciliation;

import dev.iamkavindu.reconflow.provider.SettlementView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;

public interface ReconciliationEvidenceQueries {

    List<SettlementView> settlements(TenantId tenantId, ReconciliationId reconciliationId);
}
