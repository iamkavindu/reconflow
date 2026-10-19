package dev.iamkavindu.reconflow.ai.internal;

import dev.iamkavindu.reconflow.ai.InvestigationEngine;
import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.InvestigationResult;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "reconflow.ai.enabled", havingValue = "false", matchIfMissing = true)
class DisabledInvestigationEngine implements InvestigationEngine {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public InvestigationResult investigate(TenantId tenantId, CaseId caseId) {
        throw new IllegalStateException("AI investigation is not configured");
    }

    @Override
    public String model() {
        return "unconfigured";
    }

    @Override
    public String promptVersion() {
        return "reconflow-investigation-v1";
    }
}
