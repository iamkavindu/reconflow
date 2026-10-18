package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.util.Optional;

public interface ReconciliationRepository {

    Reconciliation save(Reconciliation reconciliation);

    Optional<Reconciliation> findByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId);

    boolean existsByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId);
}
