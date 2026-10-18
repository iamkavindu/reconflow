package dev.iamkavindu.reconflow.cases.events;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.InvestigationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

public record InvestigationRequested(
        UUID eventId, TenantId tenantId, CaseId caseId, InvestigationId investigationId,
        String requestedBy, Instant requestedAt, UUID correlationId) {}
