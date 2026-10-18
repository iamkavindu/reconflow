package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.provider.SettlementQueries;
import dev.iamkavindu.reconflow.reconciliation.Discrepancy;
import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SettlementFeeEvidence {

    private final SettlementQueries settlements;

    SettlementFeeEvidence(SettlementQueries settlements) {
        this.settlements = settlements;
    }

    List<Discrepancy> findings(TransactionView internal, ProviderTransactionView provider, Instant detectedAt) {
        if (internal.type() != TransactionType.PAYMENT || !internal.currency().equals(provider.currency())
                || internal.amount().compareTo(provider.amount()) == 0) {
            return List.of();
        }
        for (var settlement : settlements.forProviderTransaction(internal.tenantId(), provider.providerTransactionId())) {
            if (!settlement.currency().equals(internal.currency())) {
                continue;
            }
            for (var line : settlement.lines()) {
                if (line.providerTransactionReference().equals(provider.providerTransactionReference())
                        && line.feeAmount().signum() > 0 && line.grossAmount().compareTo(internal.amount()) == 0
                        && line.netAmount().compareTo(provider.amount()) == 0) {
                    return List.of(new Discrepancy(UUID.randomUUID(), DiscrepancyType.FEE_VARIANCE,
                            "Reported settlement fee explains the gross/net difference; human review remains required",
                            "gross " + line.grossAmount() + " " + settlement.currency(),
                            "net " + line.netAmount() + ", reported fee " + line.feeAmount()
                                    + "; settlement " + settlement.settlementId().value(), detectedAt));
                }
            }
        }
        return List.of();
    }
}
