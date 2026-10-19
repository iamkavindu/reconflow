package dev.iamkavindu.reconflow.cases.events;

import dev.iamkavindu.reconflow.cases.InvestigationId;
import dev.iamkavindu.reconflow.cases.InvestigationStatus;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

public record InvestigationStateChanged(
        UUID eventId,
        TenantId tenantId,
        InvestigationId investigationId,
        InvestigationStatus status,
        Instant changedAt,
        UUID correlationId,
        UUID causationId) {}
