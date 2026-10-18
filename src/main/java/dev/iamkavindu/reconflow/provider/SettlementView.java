package dev.iamkavindu.reconflow.provider;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SettlementView(
        SettlementId settlementId,
        TenantId tenantId,
        String provider,
        String providerSettlementReference,
        String currency,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        Instant settledAt,
        Instant recordedAt,
        List<SettlementLine> lines) {

    public SettlementView {
        lines = List.copyOf(lines);
    }
}
