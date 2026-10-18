package dev.iamkavindu.reconflow.cases;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;

public interface InvestigationQueries {

    InvestigationView get(TenantId tenantId, InvestigationId investigationId);

    List<InvestigationView> forCase(TenantId tenantId, CaseId caseId);
}
