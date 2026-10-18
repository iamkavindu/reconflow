package dev.iamkavindu.reconflow.provider.events;

import dev.iamkavindu.reconflow.provider.SettlementId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

public record SettlementImported(
        UUID eventId, TenantId tenantId, SettlementId settlementId, Instant recordedAt,
        String recordedBy, UUID correlationId) {}
