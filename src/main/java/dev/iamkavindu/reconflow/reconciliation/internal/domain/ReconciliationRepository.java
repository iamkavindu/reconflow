package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReconciliationRepository {

    Reconciliation save(Reconciliation reconciliation);

    Optional<Reconciliation> findByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId);

    boolean existsByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId);

    Optional<Reconciliation> findByTenantIdAndId(TenantId tenantId, ReconciliationId reconciliationId);

    List<Reconciliation> findByTenantIdAndProviderTransactionId(TenantId tenantId, ProviderTransactionId providerId);

    List<Reconciliation> findPendingExpiredBefore(Instant now, int limit);
}
