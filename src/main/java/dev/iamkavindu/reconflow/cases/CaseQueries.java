package dev.iamkavindu.reconflow.cases;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;

public interface CaseQueries {

    CaseView getCase(TenantId tenantId, CaseId caseId);

    CaseView getCaseForReconciliation(TenantId tenantId, ReconciliationId reconciliationId);
}
