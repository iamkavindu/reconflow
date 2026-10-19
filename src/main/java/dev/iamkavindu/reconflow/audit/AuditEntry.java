package dev.iamkavindu.reconflow.audit;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

public record AuditEntry(
        long sequence,
        UUID eventId,
        TenantId tenantId,
        AuditResourceType resourceType,
        UUID resourceId,
        String action,
        String actor,
        Instant occurredAt,
        Instant recordedAt,
        UUID correlationId,
        UUID causationId) {}
