package dev.iamkavindu.reconflow.transactions;

import dev.iamkavindu.reconflow.tenancy.TenantId;

public interface TransactionQueries {

    TransactionView getTransaction(TenantId tenantId, TransactionId transactionId);
}
