package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.util.Objects;

public class Reconciliation {

    private final ReconciliationId reconciliationId;
    private final TenantId tenantId;
    private final TransactionId transactionId;
    private final ReconciliationStatus status;
    private final MatchingWindow matchingWindow;

    private Reconciliation(
            ReconciliationId reconciliationId,
            TenantId tenantId,
            TransactionId transactionId,
            ReconciliationStatus status,
            MatchingWindow matchingWindow) {
        this.reconciliationId = Objects.requireNonNull(reconciliationId, "reconciliationId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.matchingWindow = Objects.requireNonNull(matchingWindow, "matchingWindow must not be null");
    }

    public static Reconciliation pending(
            ReconciliationId reconciliationId,
            TenantId tenantId,
            TransactionId transactionId,
            MatchingWindow matchingWindow) {
        return new Reconciliation(
                reconciliationId, tenantId, transactionId, ReconciliationStatus.PENDING, matchingWindow);
    }

    public static Reconciliation rehydrate(
            ReconciliationId reconciliationId,
            TenantId tenantId,
            TransactionId transactionId,
            ReconciliationStatus status,
            MatchingWindow matchingWindow) {
        return new Reconciliation(reconciliationId, tenantId, transactionId, status, matchingWindow);
    }

    public ReconciliationId reconciliationId() {
        return reconciliationId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public TransactionId transactionId() {
        return transactionId;
    }

    public ReconciliationStatus status() {
        return status;
    }

    public MatchingWindow matchingWindow() {
        return matchingWindow;
    }
}
