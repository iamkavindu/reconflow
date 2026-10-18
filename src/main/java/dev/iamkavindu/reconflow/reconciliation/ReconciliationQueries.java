package dev.iamkavindu.reconflow.reconciliation;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;

public interface ReconciliationQueries {

    ReconciliationView getReconciliation(TenantId tenantId, TransactionId transactionId);
}
