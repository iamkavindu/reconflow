package dev.iamkavindu.reconflow.cases.internal.persistence;

import dev.iamkavindu.reconflow.cases.CaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cases", schema = "cases")
class CaseJpaEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "reconciliation_id", nullable = false)
    UUID reconciliationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    CaseStatus status;

    @Column(name = "opened_at", nullable = false)
    Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(nullable = false, columnDefinition = "text")
    String notes;

    @Column(columnDefinition = "text")
    String resolution;

    @Version
    @Column(nullable = false)
    Long version;

    protected CaseJpaEntity() {}

    CaseJpaEntity(
            UUID id,
            UUID tenantId,
            UUID reconciliationId,
            CaseStatus status,
            Instant openedAt,
            Instant updatedAt,
            String notes,
            String resolution,
            Long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.reconciliationId = reconciliationId;
        this.status = status;
        this.openedAt = openedAt;
        this.updatedAt = updatedAt;
        this.notes = notes;
        this.resolution = resolution;
        this.version = version;
    }
}
