package dev.iamkavindu.reconflow.transactions.internal.domain;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.util.Optional;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId);
}
