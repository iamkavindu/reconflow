package dev.iamkavindu.reconflow.ai;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.InvestigationResult;
import dev.iamkavindu.reconflow.tenancy.TenantId;

public interface InvestigationEngine {

    boolean available();

    InvestigationResult investigate(TenantId tenantId, CaseId caseId);

    String model();

    String promptVersion();
}
