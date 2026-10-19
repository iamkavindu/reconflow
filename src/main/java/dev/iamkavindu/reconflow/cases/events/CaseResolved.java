package dev.iamkavindu.reconflow.cases.events;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.Resolution;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.UUID;

public record CaseResolved(UUID eventId, TenantId tenantId, CaseId caseId, Resolution resolution) {}
