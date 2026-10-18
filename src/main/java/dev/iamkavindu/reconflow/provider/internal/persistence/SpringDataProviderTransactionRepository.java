package dev.iamkavindu.reconflow.provider.internal.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataProviderTransactionRepository extends JpaRepository<ProviderTransactionJpaEntity, UUID> {

    Optional<ProviderTransactionJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ProviderTransactionJpaEntity> findByTenantIdAndProviderAndProviderEventId(
            UUID tenantId, String provider, String providerEventId);

    Optional<ProviderTransactionJpaEntity> findByTenantIdAndProviderAndProviderTransactionReference(
            UUID tenantId, String provider, String providerTransactionReference);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    INSERT INTO provider.provider_transactions
                        (id, tenant_id, provider_event_id, provider, provider_transaction_reference,
                         business_reference, transaction_type, amount, currency, status, occurred_at,
                         recorded_at, correlation_id, causation_id)
                    VALUES
                        (:id, :tenantId, :providerEventId, :provider, :providerTransactionReference,
                         :businessReference, :transactionType, :amount, :currency, :status, :occurredAt,
                         :recordedAt, :correlationId, :causationId)
                    ON CONFLICT DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("providerEventId") String providerEventId,
            @Param("provider") String provider,
            @Param("providerTransactionReference") String providerTransactionReference,
            @Param("businessReference") String businessReference,
            @Param("transactionType") String transactionType,
            @Param("amount") BigDecimal amount,
            @Param("currency") String currency,
            @Param("status") String status,
            @Param("occurredAt") Instant occurredAt,
            @Param("recordedAt") Instant recordedAt,
            @Param("correlationId") UUID correlationId,
            @Param("causationId") UUID causationId);
}
