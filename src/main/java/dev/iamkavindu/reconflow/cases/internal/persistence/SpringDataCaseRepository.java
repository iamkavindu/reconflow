package dev.iamkavindu.reconflow.cases.internal.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataCaseRepository extends JpaRepository<CaseJpaEntity, UUID> {

    Optional<CaseJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<CaseJpaEntity> findByTenantIdAndReconciliationId(UUID tenantId, UUID reconciliationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO cases.cases
                (id, tenant_id, reconciliation_id, status, opened_at, updated_at, notes, resolution, version)
            VALUES (:id, :tenantId, :reconciliationId, 'OPEN', :openedAt, :openedAt, '[]', NULL, 0)
            ON CONFLICT (tenant_id, reconciliation_id) DO NOTHING
            """, nativeQuery = true)
    int openIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("reconciliationId") UUID reconciliationId,
            @Param("openedAt") Instant openedAt);
}
