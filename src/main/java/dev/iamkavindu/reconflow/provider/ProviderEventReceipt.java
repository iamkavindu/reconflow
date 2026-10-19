package dev.iamkavindu.reconflow.provider;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

public record ProviderEventReceipt(UUID deliveryId, TenantId tenantId, String providerEventId, UUID correlationId, Instant acceptedAt) {}
