package dev.iamkavindu.reconflow.reporting.internal;

import dev.iamkavindu.reconflow.cases.CaseListing;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationListing;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reporting.Dashboard;
import dev.iamkavindu.reconflow.reporting.DashboardQueries;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionListing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DashboardService implements DashboardQueries {
    private final TransactionListing transactions;
    private final ReconciliationListing reconciliations;
    private final CaseListing cases;

    DashboardService(TransactionListing transactions, ReconciliationListing reconciliations, CaseListing cases) {
        this.transactions = transactions;
        this.reconciliations = reconciliations;
        this.cases = cases;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dashboard get(TenantId tenantId) {
        var determinations = reconciliations.counts(tenantId);
        var workflows = cases.counts(tenantId);
        return new Dashboard(
                transactions.count(tenantId),
                determinations.getOrDefault(ReconciliationStatus.MATCHED, 0L),
                determinations.getOrDefault(ReconciliationStatus.REVIEW_REQUIRED, 0L),
                determinations.getOrDefault(ReconciliationStatus.UNMATCHED, 0L),
                workflows.getOrDefault(CaseStatus.OPEN, 0L) + workflows.getOrDefault(CaseStatus.UNDER_REVIEW, 0L),
                workflows.getOrDefault(CaseStatus.RESOLVED, 0L));
    }
}
