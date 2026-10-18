package dev.iamkavindu.reconflow.provider.internal.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends Repository<SettlementJpaEntity, UUID> {

    @Modifying
    @Query(value = """
                    INSERT INTO provider.settlements
                        (id, tenant_id, provider, provider_settlement_reference, currency,
                         gross_amount, fee_amount, net_amount, settled_at, recorded_at, lines)
                    VALUES (:id, :tenant, :provider, :reference, :currency, :gross, :fee, :net, :settled, :recorded, :lines)
                    ON CONFLICT (tenant_id, provider, provider_settlement_reference) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id, @Param("tenant") UUID tenantId,
            @Param("provider") String provider, @Param("reference") String reference,
            @Param("currency") String currency, @Param("gross") BigDecimal gross,
            @Param("fee") BigDecimal fee, @Param("net") BigDecimal net,
            @Param("settled") Instant settledAt, @Param("recorded") Instant recordedAt,
            @Param("lines") String lines);
}
