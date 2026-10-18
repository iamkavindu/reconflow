package dev.iamkavindu.reconflow.provider;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record IngestProviderEventCommand(
        TenantId tenantId,
        String eventId,
        String provider,
        String providerTransactionReference,
        String businessReference,
        ProviderTransactionType type,
        BigDecimal amount,
        Currency currency,
        ProviderTransactionStatus status,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId) {}
