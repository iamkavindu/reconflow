package dev.iamkavindu.reconflow.cases;

import dev.iamkavindu.reconflow.tenancy.TenantId;

/** Internal workflow operations; these are never exposed as model tools or HTTP writes. */
public interface InvestigationProcessing {

    InvestigationView start(TenantId tenantId, InvestigationId investigationId);

    InvestigationView complete(TenantId tenantId, InvestigationId investigationId,
            InvestigationResult result, String model, String promptVersion);

    InvestigationView fail(TenantId tenantId, InvestigationId investigationId, String safeReason);
}
