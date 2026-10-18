package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.provider.ProviderTransactionCandidates;
import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.reconciliation.Discrepancy;
import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationMatched;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationReviewRequired;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationUnmatched;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.DiscrepancyDetector;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ExceptionDecision;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.MatchDecision;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationEvaluator;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationRepository;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionCandidates;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AutomaticReconciliation {

    private final TransactionCandidates transactions;
    private final ProviderTransactionCandidates providerTransactions;
    private final ReconciliationRepository reconciliations;
    private final ReconciliationPolicy policy;
    private final CandidateLock candidateLock;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final ReconciliationEvaluator evaluator = new ReconciliationEvaluator();
    private final DiscrepancyDetector discrepancyDetector = new DiscrepancyDetector();

    AutomaticReconciliation(
            TransactionCandidates transactions,
            ProviderTransactionCandidates providerTransactions,
            ReconciliationRepository reconciliations,
            ReconciliationPolicy policy,
            CandidateLock candidateLock,
            Clock clock,
            ApplicationEventPublisher events) {
        this.transactions = transactions;
        this.providerTransactions = providerTransactions;
        this.reconciliations = reconciliations;
        this.policy = policy;
        this.candidateLock = candidateLock;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public void attempt(TenantId tenantId, String reference, UUID correlationId, UUID causationId) {
        candidateLock.acquire(tenantId, reference);
        var internal = transactions.findByBusinessReference(tenantId, reference);
        var external = providerTransactions.findByBusinessReference(tenantId, reference);

        for (var transaction : internal) {
            if (!reconciliations.existsByTenantIdAndTransactionId(tenantId, transaction.transactionId())) {
                String transactionType = transaction.type().name();
                var providers = external.stream()
                        .filter(candidate -> candidate.type().name().equals(transactionType))
                        .toList();
                Reconciliation providerOrigin = null;
                if (!providers.isEmpty()) {
                    var providerId = providers.getFirst().providerTransactionId();
                    var providerLinks = reconciliations.findByTenantIdAndProviderTransactionId(tenantId, providerId);
                    providerOrigin = providerLinks.stream()
                            .filter(candidate -> candidate.transactionId() == null)
                            .filter(candidate -> candidate.status() == ReconciliationStatus.PENDING)
                            .filter(candidate -> !candidate.matchingWindow().isExpired(transaction.recordedAt()))
                            .findFirst()
                            .orElse(null);
                }
                var pending = providerOrigin == null
                        ? Reconciliation.pending(
                                new ReconciliationId(UUID.randomUUID()),
                                tenantId,
                                transaction.transactionId(),
                                policy.matchingWindowStartingAt(transaction.recordedAt()),
                                reference)
                        : providerOrigin.attachTransaction(transaction.transactionId());
                reconciliations.save(pending);
            }
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        for (var transaction : internal) {
            var reconciliation = reconciliations
                    .findByTenantIdAndTransactionId(tenantId, transaction.transactionId())
                    .orElseThrow();
            if (reconciliation.status() != ReconciliationStatus.PENDING) {
                continue;
            }

            var sameType = external.stream()
                    .filter(candidate ->
                            candidate.type().name().equals(transaction.type().name()))
                    .toList();
            var candidates = (sameType.isEmpty() ? external : sameType)
                    .stream()
                            .filter(candidate ->
                                    availableProvider(tenantId, candidate.providerTransactionId(), reconciliation))
                            .toList();
            long internalCount = internal.stream()
                    .filter(candidate -> candidate.type() == transaction.type())
                    .count();

            if (internalCount > 1 || candidates.size() > 1) {
                var discrepancy = new Discrepancy(
                        UUID.randomUUID(),
                        DiscrepancyType.DUPLICATE_TRANSACTION,
                        "Multiple financial records share the business reference and transaction type",
                        "one internal and one provider candidate",
                        internalCount + " internal / " + candidates.size() + " provider candidates",
                        now);
                Instant duplicateArrivedAt = internal.stream()
                        .filter(candidate -> candidate.type() == transaction.type())
                        .map(candidate -> candidate.recordedAt())
                        .reduce(transaction.recordedAt(), (left, right) -> left.isAfter(right) ? left : right);
                for (var candidate : candidates) {
                    if (candidate.recordedAt().isAfter(duplicateArrivedAt)) {
                        duplicateArrivedAt = candidate.recordedAt();
                    }
                }
                if (!reconciliation.matchingWindow().isExpired(duplicateArrivedAt)) {
                    requireReview(
                            reconciliation,
                            candidates,
                            now,
                            duplicateArrivedAt,
                            List.of(),
                            List.of(discrepancy),
                            correlationId,
                            causationId);
                } else {
                    expireMissing(reconciliation, now, correlationId, causationId);
                }
                continue;
            }
            if (candidates.isEmpty()) {
                if (reconciliation.matchingWindow().isExpired(now)) {
                    expireMissing(reconciliation, now, correlationId, causationId);
                }
                continue;
            }

            var providerTransaction = candidates.getFirst();
            Instant arrivedAt = transaction.recordedAt().isAfter(providerTransaction.recordedAt())
                    ? transaction.recordedAt()
                    : providerTransaction.recordedAt();
            if (reconciliation.matchingWindow().isExpired(arrivedAt)) {
                if (reconciliation.matchingWindow().isExpired(now)) {
                    expireMissing(reconciliation, now, correlationId, causationId);
                }
                continue;
            }
            var evaluations =
                    evaluator.evaluate(transaction, providerTransaction, policy.maximumTransactionTimeDifference());
            var discrepancies = discrepancyDetector.detect(evaluations, transaction.type(), now);
            if (!discrepancies.isEmpty()) {
                requireReview(
                        reconciliation,
                        candidates,
                        now,
                        arrivedAt,
                        evaluations,
                        discrepancies,
                        correlationId,
                        causationId);
                continue;
            }

            var decision = new MatchDecision(tenantId, providerTransaction.providerTransactionId(), now, evaluations);
            var matched = reconciliations.save(reconciliation.match(decision, arrivedAt));
            events.publishEvent(new ReconciliationMatched(
                    UUID.randomUUID(),
                    tenantId,
                    matched.reconciliationId(),
                    transaction.transactionId(),
                    providerTransaction.providerTransactionId(),
                    now,
                    evaluations,
                    correlationId,
                    causationId));
        }

        for (var provider : external) {
            var providerId = provider.providerTransactionId();
            var links = reconciliations.findByTenantIdAndProviderTransactionId(tenantId, providerId);
            if (links.isEmpty()) {
                var pending = reconciliations.save(Reconciliation.pendingProvider(
                        new ReconciliationId(UUID.randomUUID()),
                        tenantId,
                        provider.providerTransactionId(),
                        policy.matchingWindowStartingAt(provider.recordedAt()),
                        reference));
                links = List.of(pending);
            }
            for (var pending : links) {
                if (pending.status() != ReconciliationStatus.PENDING || pending.transactionId() != null) {
                    continue;
                }
                if (pending.matchingWindow().isExpired(now)) {
                    expireMissing(pending, now, correlationId, causationId);
                } else if (alreadyAccountedFor(tenantId, internal, provider)) {
                    var discrepancy = new Discrepancy(
                            UUID.randomUUID(),
                            DiscrepancyType.DUPLICATE_TRANSACTION,
                            "An additional provider record has an internal reference already accounted for",
                            "one logical provider record",
                            "additional provider record " + provider.providerTransactionReference(),
                            now);
                    requireReview(
                            pending,
                            List.of(provider),
                            now,
                            now,
                            List.of(),
                            List.of(discrepancy),
                            correlationId,
                            causationId);
                }
            }
        }
    }

    private boolean availableProvider(TenantId tenantId, ProviderTransactionId providerId, Reconciliation current) {
        var links = reconciliations.findByTenantIdAndProviderTransactionId(tenantId, providerId);
        return links.stream()
                .allMatch(link -> link.reconciliationId().equals(current.reconciliationId())
                        || (link.status() == ReconciliationStatus.PENDING && link.transactionId() == null));
    }

    private boolean alreadyAccountedFor(
            TenantId tenantId, List<TransactionView> internal, ProviderTransactionView provider) {
        for (var transaction : internal) {
            if (transaction.type().name().equals(provider.type().name())) {
                var existing = reconciliations.findByTenantIdAndTransactionId(tenantId, transaction.transactionId());
                if (existing.isPresent()
                        && (existing.get().status() == ReconciliationStatus.MATCHED
                                || existing.get().status() == ReconciliationStatus.REVIEW_REQUIRED)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void requireReview(
            Reconciliation reconciliation,
            List<ProviderTransactionView> candidates,
            Instant now,
            Instant arrivedAt,
            List<RuleEvaluation> evaluations,
            List<Discrepancy> discrepancies,
            UUID correlationId,
            UUID causationId) {
        var decision = new ExceptionDecision(
                reconciliation.tenantId(),
                candidates.stream()
                        .map(ProviderTransactionView::providerTransactionId)
                        .toList(),
                now,
                evaluations,
                discrepancies);
        var review = reconciliations.save(reconciliation.requireReview(decision, arrivedAt));
        events.publishEvent(new ReconciliationReviewRequired(
                UUID.randomUUID(),
                review.tenantId(),
                review.reconciliationId(),
                now,
                discrepancies,
                correlationId,
                causationId));
    }

    private void expireMissing(Reconciliation reconciliation, Instant now, UUID correlationId, UUID causationId) {
        boolean missingProvider = reconciliation.transactionId() != null;
        var discrepancy = new Discrepancy(
                UUID.randomUUID(),
                missingProvider
                        ? DiscrepancyType.MISSING_PROVIDER_TRANSACTION
                        : DiscrepancyType.MISSING_INTERNAL_TRANSACTION,
                "No available counterpart arrived before the matching deadline",
                "counterpart before " + reconciliation.matchingWindow().expiresAt(),
                "no timely available counterpart",
                now);
        var providers = reconciliation.providerOriginId() == null
                ? List.<ProviderTransactionId>of()
                : List.of(reconciliation.providerOriginId());
        var decision =
                new ExceptionDecision(reconciliation.tenantId(), providers, now, List.of(), List.of(discrepancy));
        var unmatched = reconciliations.save(reconciliation.expire(decision));
        events.publishEvent(new ReconciliationUnmatched(
                UUID.randomUUID(),
                unmatched.tenantId(),
                unmatched.reconciliationId(),
                now,
                decision.discrepancies(),
                correlationId,
                causationId));
    }
}
