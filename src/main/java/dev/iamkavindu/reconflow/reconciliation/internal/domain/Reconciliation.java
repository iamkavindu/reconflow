package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;
import java.util.Objects;

public class Reconciliation {

    private final ReconciliationId reconciliationId;
    private final TenantId tenantId;
    private final TransactionId transactionId;
    private final ReconciliationStatus status;
    private final MatchingWindow matchingWindow;
    private final String businessReference;
    private final ProviderTransactionId providerOriginId;
    private final MatchDecision matchDecision;
    private final ExceptionDecision exceptionDecision;

    private Reconciliation(
            ReconciliationId reconciliationId,
            TenantId tenantId,
            TransactionId transactionId,
            ReconciliationStatus status,
            MatchingWindow matchingWindow,
            String businessReference,
            ProviderTransactionId providerOriginId,
            MatchDecision matchDecision,
            ExceptionDecision exceptionDecision) {
        this.reconciliationId = Objects.requireNonNull(reconciliationId, "reconciliationId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.transactionId = transactionId;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.matchingWindow = Objects.requireNonNull(matchingWindow, "matchingWindow must not be null");
        this.businessReference = Objects.requireNonNull(businessReference, "businessReference must not be null");
        this.providerOriginId = providerOriginId;
        this.matchDecision = matchDecision;
        this.exceptionDecision = exceptionDecision;
        if (businessReference.isBlank() || (transactionId == null && providerOriginId == null)) {
            throw new IllegalArgumentException("a reconciliation requires a reference and a source financial record");
        }
        if ((status == ReconciliationStatus.MATCHED) != (matchDecision != null)) {
            throw new IllegalArgumentException("only a matched reconciliation must have a match decision");
        }
        if (matchDecision != null && (!tenantId.equals(matchDecision.tenantId()) || transactionId == null)) {
            throw new IllegalArgumentException("a match requires an internal transaction in the same tenant");
        }
        boolean requiresException =
                status == ReconciliationStatus.REVIEW_REQUIRED || status == ReconciliationStatus.UNMATCHED;
        if (requiresException != (exceptionDecision != null)) {
            throw new IllegalArgumentException("an exception outcome must have an exception decision");
        }
        if (exceptionDecision != null && !tenantId.equals(exceptionDecision.tenantId())) {
            throw new IllegalArgumentException("exception decision must belong to the same tenant");
        }
    }

    public static Reconciliation pending(
            ReconciliationId id,
            TenantId tenantId,
            TransactionId transactionId,
            MatchingWindow window,
            String reference) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        return new Reconciliation(
                id, tenantId, transactionId, ReconciliationStatus.PENDING, window, reference, null, null, null);
    }

    public static Reconciliation pendingProvider(
            ReconciliationId id,
            TenantId tenantId,
            ProviderTransactionId providerId,
            MatchingWindow window,
            String reference) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        return new Reconciliation(
                id, tenantId, null, ReconciliationStatus.PENDING, window, reference, providerId, null, null);
    }

    public static Reconciliation rehydrate(
            ReconciliationId id,
            TenantId tenantId,
            TransactionId transactionId,
            ReconciliationStatus status,
            MatchingWindow window,
            String reference,
            ProviderTransactionId providerOriginId,
            MatchDecision match,
            ExceptionDecision exception) {
        return new Reconciliation(
                id, tenantId, transactionId, status, window, reference, providerOriginId, match, exception);
    }

    public Reconciliation attachTransaction(TransactionId id) {
        requirePending();
        if (transactionId != null) {
            throw new IllegalStateException("reconciliation already has an internal transaction");
        }
        return new Reconciliation(
                reconciliationId,
                tenantId,
                Objects.requireNonNull(id),
                status,
                matchingWindow,
                businessReference,
                providerOriginId,
                null,
                null);
    }

    public Reconciliation match(MatchDecision decision) {
        return match(decision, decision.matchedAt());
    }

    public Reconciliation match(MatchDecision decision, Instant counterpartArrivedAt) {
        requirePending();
        requireTimelyCounterpart(counterpartArrivedAt);
        if (decision.matchedAt().isBefore(counterpartArrivedAt)) {
            throw new IllegalArgumentException("match decision cannot precede counterpart arrival");
        }
        return new Reconciliation(
                reconciliationId,
                tenantId,
                transactionId,
                ReconciliationStatus.MATCHED,
                matchingWindow,
                businessReference,
                providerOriginId,
                decision,
                null);
    }

    public Reconciliation requireReview(ExceptionDecision decision) {
        return requireReview(decision, decision.decidedAt());
    }

    public Reconciliation requireReview(ExceptionDecision decision, Instant counterpartArrivedAt) {
        requirePending();
        requireTimelyCounterpart(counterpartArrivedAt);
        if (decision.decidedAt().isBefore(counterpartArrivedAt)) {
            throw new IllegalArgumentException("review decision cannot precede counterpart arrival");
        }
        return new Reconciliation(
                reconciliationId,
                tenantId,
                transactionId,
                ReconciliationStatus.REVIEW_REQUIRED,
                matchingWindow,
                businessReference,
                providerOriginId,
                null,
                decision);
    }

    public Reconciliation expire(ExceptionDecision decision) {
        requirePending();
        if (!matchingWindow.isExpired(decision.decidedAt())) {
            throw new IllegalStateException("a reconciliation cannot expire before its matching deadline");
        }
        var missingType = transactionId == null
                ? DiscrepancyType.MISSING_INTERNAL_TRANSACTION
                : DiscrepancyType.MISSING_PROVIDER_TRANSACTION;
        if (decision.discrepancies().stream().noneMatch(discrepancy -> discrepancy.type() == missingType)) {
            throw new IllegalArgumentException("expiry requires a discrepancy identifying the missing counterpart");
        }
        return new Reconciliation(
                reconciliationId,
                tenantId,
                transactionId,
                ReconciliationStatus.UNMATCHED,
                matchingWindow,
                businessReference,
                providerOriginId,
                null,
                decision);
    }

    private void requirePending() {
        if (status != ReconciliationStatus.PENDING) {
            throw new IllegalStateException("only a pending reconciliation can change its determination");
        }
    }

    private void requireTimelyCounterpart(Instant arrivedAt) {
        Objects.requireNonNull(arrivedAt, "counterpartArrivedAt must not be null");
        if (arrivedAt.isBefore(matchingWindow.startedAt()) || matchingWindow.isExpired(arrivedAt)) {
            throw new IllegalArgumentException("counterpart must arrive within the matching window");
        }
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

    public String businessReference() {
        return businessReference;
    }

    public ProviderTransactionId providerOriginId() {
        return providerOriginId;
    }

    public MatchDecision matchDecision() {
        return matchDecision;
    }

    public ExceptionDecision exceptionDecision() {
        return exceptionDecision;
    }
}
