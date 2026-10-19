package dev.iamkavindu.reconflow.cases.events;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseNote;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.UUID;

public record CaseNoteAdded(UUID eventId, TenantId tenantId, CaseId caseId, CaseNote note) {}
