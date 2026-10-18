package dev.iamkavindu.reconflow.cases.events;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

public record CaseOpened(
        UUID eventId,
        TenantId tenantId,
        CaseId caseId,
        ReconciliationId reconciliationId,
        Instant openedAt,
        UUID correlationId,
        UUID causationId) {}
