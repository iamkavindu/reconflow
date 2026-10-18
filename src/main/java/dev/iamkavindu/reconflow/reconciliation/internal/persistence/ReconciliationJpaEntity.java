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

    @Column(name = "provider_transaction_id")
    private UUID providerTransactionId;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "rule_evaluations", columnDefinition = "text")
    private String ruleEvaluations;

    protected ReconciliationJpaEntity() {}

    ReconciliationJpaEntity(
            UUID id,
            UUID tenantId,
            UUID transactionId,
            ReconciliationStatus status,
            Instant startedAt,
            Instant expiresAt,
            UUID providerTransactionId,
            Instant matchedAt,
            String ruleEvaluations) {
        this.id = id;
        this.tenantId = tenantId;
        this.transactionId = transactionId;
        this.status = status;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
        this.providerTransactionId = providerTransactionId;
        this.matchedAt = matchedAt;
        this.ruleEvaluations = ruleEvaluations;
    }

    UUID providerTransactionId() {
        return providerTransactionId;
    }

    Instant matchedAt() {
        return matchedAt;
    }

    String ruleEvaluations() {
        return ruleEvaluations;
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
