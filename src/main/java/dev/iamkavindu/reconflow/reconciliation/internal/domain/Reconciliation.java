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
    private final MatchDecision matchDecision;

    private Reconciliation(
            ReconciliationId reconciliationId,
            TenantId tenantId,
            TransactionId transactionId,
            ReconciliationStatus status,
            MatchingWindow matchingWindow) {
        this(reconciliationId, tenantId, transactionId, status, matchingWindow, null);
    }

    private Reconciliation(
            ReconciliationId reconciliationId,
            TenantId tenantId,
            TransactionId transactionId,
            ReconciliationStatus status,
            MatchingWindow matchingWindow,
            MatchDecision matchDecision) {
        this.reconciliationId = Objects.requireNonNull(reconciliationId, "reconciliationId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.matchingWindow = Objects.requireNonNull(matchingWindow, "matchingWindow must not be null");
        this.matchDecision = matchDecision;
        if ((status == ReconciliationStatus.MATCHED) != (matchDecision != null)) {
            throw new IllegalArgumentException("only a matched reconciliation must have a match decision");
        }
        if (matchDecision != null && !tenantId.equals(matchDecision.tenantId())) {
            throw new IllegalArgumentException("match decision must belong to the same tenant");
        }
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
            MatchingWindow matchingWindow,
            MatchDecision matchDecision) {
        return new Reconciliation(reconciliationId, tenantId, transactionId, status, matchingWindow, matchDecision);
    }

    public Reconciliation match(MatchDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        if (status != ReconciliationStatus.PENDING) {
            throw new IllegalStateException("only a pending reconciliation can be matched");
        }
        if (decision.matchedAt().isBefore(matchingWindow.startedAt())
                || matchingWindow.isExpired(decision.matchedAt())) {
            throw new IllegalArgumentException("match must occur within the matching window");
        }
        return new Reconciliation(
                reconciliationId, tenantId, transactionId, ReconciliationStatus.MATCHED, matchingWindow, decision);
    }

    public MatchDecision matchDecision() {
        return matchDecision;
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
