package dev.iamkavindu.reconflow.transactions.internal.persistence;

import dev.iamkavindu.reconflow.transactions.TransactionStatus;
import dev.iamkavindu.reconflow.transactions.TransactionType;
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
@Table(name = "transactions", schema = "transactions")
class TransactionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "business_reference", nullable = false, length = 100)
    private String businessReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected TransactionJpaEntity() {}

    TransactionJpaEntity(
            UUID id,
            UUID tenantId,
            String businessReference,
            TransactionType type,
            BigDecimal amount,
            String currency,
            Instant occurredAt,
            TransactionStatus status,
            Instant recordedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.businessReference = businessReference;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.occurredAt = occurredAt;
        this.status = status;
        this.recordedAt = recordedAt;
    }

    UUID id() {
        return id;
    }

    UUID tenantId() {
        return tenantId;
    }

    String businessReference() {
        return businessReference;
    }

    TransactionType type() {
        return type;
    }

    BigDecimal amount() {
        return amount;
    }

    String currency() {
        return currency;
    }

    Instant occurredAt() {
        return occurredAt;
    }

    TransactionStatus status() {
        return status;
    }

    Instant recordedAt() {
        return recordedAt;
    }
}
