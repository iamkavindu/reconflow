package dev.iamkavindu.reconflow.provider.events;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProviderTransactionRecorded(
        UUID eventId,
        TenantId tenantId,
        ProviderTransactionId providerTransactionId,
        String provider,
        String providerTransactionReference,
        String businessReference,
        ProviderTransactionType type,
        BigDecimal amount,
        String currency,
        ProviderTransactionStatus status,
        Instant occurredAt,
        Instant recordedAt,
        UUID correlationId,
        UUID causationId) {

    public ProviderTransactionRecorded {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(providerTransactionId, "providerTransactionId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(providerTransactionReference, "providerTransactionReference must not be null");
        Objects.requireNonNull(businessReference, "businessReference must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(causationId, "causationId must not be null");
    }
}
