package dev.iamkavindu.reconflow.cases;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;

public record InvestigationView(
        InvestigationId investigationId,
        TenantId tenantId,
        CaseId caseId,
        String type,
        InvestigationStatus status,
        String requestedBy,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        InvestigationResult result,
        String failureReason,
        String model,
        String promptVersion) {}
