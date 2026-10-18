package dev.iamkavindu.reconflow.reconciliation.internal.persistence;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliations", schema = "reconciliation")
class ReconciliationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected ReconciliationJpaEntity() {}

    ReconciliationJpaEntity(
            UUID id,
            UUID tenantId,
            UUID transactionId,
            ReconciliationStatus status,
            Instant startedAt,
            Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.transactionId = transactionId;
        this.status = status;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    UUID id() {
        return id;
    }

    UUID tenantId() {
        return tenantId;
    }

    UUID transactionId() {
        return transactionId;
    }

    ReconciliationStatus status() {
        return status;
    }

    Instant startedAt() {
        return startedAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }
}
