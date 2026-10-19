package dev.iamkavindu.reconflow.provider;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

public record ImportSettlementCommand(
        TenantId tenantId,
        String provider,
        String providerSettlementReference,
        Currency currency,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        Instant settledAt,
        List<SettlementLine> lines) {

    public ImportSettlementCommand {
        lines = List.copyOf(lines);
    }
}
