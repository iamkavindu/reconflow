package dev.iamkavindu.reconflow.provider.internal.persistence;

import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_transactions", schema = "provider")
class ProviderTransactionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_event_id", nullable = false, length = 200)
    private String providerEventId;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_transaction_reference", nullable = false, length = 200)
    private String providerTransactionReference;

    @Column(name = "business_reference", nullable = false, length = 100)
    private String businessReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private ProviderTransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderTransactionStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "causation_id", nullable = false)
    private UUID causationId;

    protected ProviderTransactionJpaEntity() {}

    ProviderTransactionJpaEntity(
            UUID id,
            UUID tenantId,
            String providerEventId,
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
        this.id = id;
        this.tenantId = tenantId;
        this.providerEventId = providerEventId;
        this.provider = provider;
        this.providerTransactionReference = providerTransactionReference;
        this.businessReference = businessReference;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.correlationId = correlationId;
        this.causationId = causationId;
    }

    UUID id() {
        return id;
    }

    UUID tenantId() {
        return tenantId;
    }

    String providerEventId() {
        return providerEventId;
    }

    String provider() {
        return provider;
    }

    String providerTransactionReference() {
        return providerTransactionReference;
    }

    String businessReference() {
        return businessReference;
    }

    ProviderTransactionType type() {
        return type;
    }

    BigDecimal amount() {
        return amount;
    }

    String currency() {
        return currency;
    }

    ProviderTransactionStatus status() {
        return status;
    }

    Instant occurredAt() {
        return occurredAt;
    }

    Instant recordedAt() {
        return recordedAt;
    }

    UUID correlationId() {
        return correlationId;
    }

    UUID causationId() {
        return causationId;
    }
}
