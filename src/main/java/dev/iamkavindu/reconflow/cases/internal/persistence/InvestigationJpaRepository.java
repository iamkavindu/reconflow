package dev.iamkavindu.reconflow.cases.internal.persistence;

import dev.iamkavindu.reconflow.cases.InvestigationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestigationJpaRepository extends JpaRepository<InvestigationJpaEntity, UUID> {

    Optional<InvestigationJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<InvestigationJpaEntity> findFirstByTenantIdAndCaseIdAndStatusIn(
            UUID tenantId, UUID caseId, Collection<InvestigationStatus> statuses);

    List<InvestigationJpaEntity> findTop100ByTenantIdAndCaseIdOrderByRequestedAtDescIdDesc(UUID tenantId, UUID caseId);

    @Modifying
    @Query(value = """
                    INSERT INTO cases.investigations
                        (id, tenant_id, case_id, status, requested_by, requested_at, version)
                    VALUES (:id, :tenant, :caseId, 'REQUESTED', :actor, :requested, 0)
                    ON CONFLICT DO NOTHING
                    """, nativeQuery = true)
    int requestIfAbsent(
            @Param("id") UUID id,
            @Param("tenant") UUID tenant,
            @Param("caseId") UUID caseId,
            @Param("actor") String actor,
            @Param("requested") Instant requestedAt);
}
