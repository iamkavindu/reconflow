package dev.iamkavindu.reconflow.transactions.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTransactionRepository extends JpaRepository<TransactionJpaEntity, UUID> {

    Optional<TransactionJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
