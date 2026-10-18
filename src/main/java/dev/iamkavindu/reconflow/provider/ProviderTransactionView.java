package dev.iamkavindu.reconflow.provider;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

public record ProviderTransactionView(
        ProviderTransactionId providerTransactionId,
        TenantId tenantId,
        String eventId,
        String provider,
        String providerTransactionReference,
        String businessReference,
        ProviderTransactionType type,
        BigDecimal amount,
        String currency,
        ProviderTransactionStatus status,
        Instant occurredAt,
        Instant recordedAt) {}
