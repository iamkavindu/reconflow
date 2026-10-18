package dev.iamkavindu.reconflow.cases.internal.persistence;

import dev.iamkavindu.reconflow.cases.InvestigationStatus;
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
@Table(name = "investigations", schema = "cases")
class InvestigationJpaEntity {

    @Id
    UUID id;

    UUID tenantId;
    UUID caseId;

    @Enumerated(EnumType.STRING)
    InvestigationStatus status;

    String requestedBy;
    Instant requestedAt;
    Instant startedAt;
    Instant completedAt;

    @Column(columnDefinition = "TEXT")
    String result;

    String failureReason;
    String model;
    String promptVersion;

    @Version
    Long version;

    protected InvestigationJpaEntity() {}
}
