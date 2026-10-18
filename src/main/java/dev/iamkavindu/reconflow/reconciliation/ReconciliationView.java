package dev.iamkavindu.reconflow.reconciliation;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;

public record ReconciliationView(
        ReconciliationId reconciliationId,
        TenantId tenantId,
        TransactionId transactionId,
        ReconciliationStatus status,
        Instant startedAt,
        Instant expiresAt) {}
