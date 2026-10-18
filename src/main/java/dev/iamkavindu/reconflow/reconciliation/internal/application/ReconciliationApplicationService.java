package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationRepository;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReconciliationApplicationService implements ReconciliationQueries {

    private final ReconciliationRepository reconciliationRepository;

    ReconciliationApplicationService(ReconciliationRepository reconciliationRepository) {
        this.reconciliationRepository = reconciliationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationView getReconciliation(TenantId tenantId, TransactionId transactionId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");

        return reconciliationRepository
                .findByTenantIdAndTransactionId(tenantId, transactionId)
                .map(ReconciliationApplicationService::toView)
                .orElseThrow(() -> new NoSuchElementException("Reconciliation not found"));
    }

    private static ReconciliationView toView(Reconciliation reconciliation) {
        return new ReconciliationView(
                reconciliation.reconciliationId(),
                reconciliation.tenantId(),
                reconciliation.transactionId(),
                reconciliation.status(),
                reconciliation.matchingWindow().startedAt(),
                reconciliation.matchingWindow().expiresAt());
    }
}
