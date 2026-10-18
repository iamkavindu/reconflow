package dev.iamkavindu.reconflow.reconciliation.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReconciliationRepository extends JpaRepository<ReconciliationJpaEntity, UUID> {

    Optional<ReconciliationJpaEntity> findByTenantIdAndTransactionId(UUID tenantId, UUID transactionId);

    boolean existsByTenantIdAndTransactionId(UUID tenantId, UUID transactionId);
}
