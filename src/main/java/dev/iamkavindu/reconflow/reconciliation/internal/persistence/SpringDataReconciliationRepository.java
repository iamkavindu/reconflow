package dev.iamkavindu.reconflow.reconciliation.internal.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataReconciliationRepository extends JpaRepository<ReconciliationJpaEntity, UUID> {

    Optional<ReconciliationJpaEntity> findByTenantIdAndTransactionId(UUID tenantId, UUID transactionId);

    boolean existsByTenantIdAndTransactionId(UUID tenantId, UUID transactionId);

    Optional<ReconciliationJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query(value = """
            SELECT * FROM reconciliation.reconciliations
            WHERE tenant_id = :tenantId
              AND (provider_transaction_id = :providerId OR provider_origin_id = :providerId
                   OR (CAST(exception_decision AS jsonb) -> 'providerTransactionIds') @> CAST(:providerLink AS jsonb))
            ORDER BY id
            """, nativeQuery = true)
    List<ReconciliationJpaEntity> findProviderLinks(
            @Param("tenantId") UUID tenantId,
            @Param("providerId") UUID providerId,
            @Param("providerLink") String providerLink);

    @Query(value = """
            SELECT * FROM reconciliation.reconciliations
            WHERE status = 'PENDING' AND expires_at <= :now
            ORDER BY expires_at, id LIMIT :batchSize
            """, nativeQuery = true)
    List<ReconciliationJpaEntity> findExpired(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
