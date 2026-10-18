package dev.iamkavindu.reconflow.provider.internal.domain;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ProviderTransaction {

    private final ProviderTransactionId providerTransactionId;
    private final TenantId tenantId;
    private final ProviderEventId providerEventId;
    private final ProviderCode provider;
    private final ProviderReference providerReference;
    private final ProviderBusinessReference businessReference;
    private final ProviderTransactionType type;
    private final ProviderMoney money;
    private final ProviderTransactionStatus status;
    private final Instant occurredAt;
    private final Instant recordedAt;
    private final UUID correlationId;
    private final UUID causationId;

    private ProviderTransaction(
            ProviderTransactionId providerTransactionId,
            TenantId tenantId,
            ProviderEventId providerEventId,
            ProviderCode provider,
            ProviderReference providerReference,
            ProviderBusinessReference businessReference,
            ProviderTransactionType type,
            ProviderMoney money,
            ProviderTransactionStatus status,
            Instant occurredAt,
            Instant recordedAt,
            UUID correlationId,
            UUID causationId) {
        this.providerTransactionId =
                Objects.requireNonNull(providerTransactionId, "providerTransactionId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.providerEventId = Objects.requireNonNull(providerEventId, "providerEventId must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.providerReference = Objects.requireNonNull(providerReference, "providerReference must not be null");
        this.businessReference = Objects.requireNonNull(businessReference, "businessReference must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.money = Objects.requireNonNull(money, "money must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.causationId = Objects.requireNonNull(causationId, "causationId must not be null");
    }

    public static ProviderTransaction record(
            ProviderTransactionId providerTransactionId,
            TenantId tenantId,
            ProviderEventId providerEventId,
            ProviderCode provider,
            ProviderReference providerReference,
            ProviderBusinessReference businessReference,
            ProviderTransactionType type,
            ProviderMoney money,
            ProviderTransactionStatus status,
            Instant occurredAt,
            Instant recordedAt,
            UUID correlationId,
            UUID causationId) {
        return new ProviderTransaction(
                providerTransactionId,
                tenantId,
                providerEventId,
                provider,
                providerReference,
                businessReference,
                type,
                money,
                status,
                occurredAt,
                recordedAt,
                correlationId,
                causationId);
    }

    public ProviderTransactionId providerTransactionId() {
        return providerTransactionId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public ProviderEventId providerEventId() {
        return providerEventId;
    }

    public ProviderCode provider() {
        return provider;
    }

    public ProviderReference providerReference() {
        return providerReference;
    }

    public ProviderBusinessReference businessReference() {
        return businessReference;
    }

    public ProviderTransactionType type() {
        return type;
    }

    public ProviderMoney money() {
        return money;
    }

    public ProviderTransactionStatus status() {
        return status;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public UUID correlationId() {
        return correlationId;
    }

    public UUID causationId() {
        return causationId;
    }
}
