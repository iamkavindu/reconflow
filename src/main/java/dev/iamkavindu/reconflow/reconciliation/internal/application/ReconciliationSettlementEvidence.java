package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.SettlementQueries;
import dev.iamkavindu.reconflow.provider.SettlementView;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationEvidenceQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReconciliationSettlementEvidence implements ReconciliationEvidenceQueries {

    private final ReconciliationQueries reconciliations;
    private final SettlementQueries settlements;

    ReconciliationSettlementEvidence(ReconciliationQueries reconciliations, SettlementQueries settlements) {
        this.reconciliations = reconciliations;
        this.settlements = settlements;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementView> settlements(TenantId tenantId, ReconciliationId reconciliationId) {
        var reconciliation = reconciliations.getReconciliationById(tenantId, reconciliationId);
        var ids = new LinkedHashSet<ProviderTransactionId>(reconciliation.comparedProviderTransactionIds());
        if (reconciliation.providerOriginId() != null) {
            ids.add(reconciliation.providerOriginId());
        }
        return ids.stream().flatMap(id -> settlements.forProviderTransaction(tenantId, id).stream()).distinct().toList();
    }
}
