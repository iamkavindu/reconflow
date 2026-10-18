package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.provider.ProviderTransactionCandidates;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.reconciliation.Discrepancy;
import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationMatched;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationReviewRequired;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.DiscrepancyDetector;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ExceptionDecision;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.MatchDecision;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationEvaluator;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationRepository;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionCandidates;
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
                reconciliations.save(Reconciliation.pending(
                        new ReconciliationId(UUID.randomUUID()),
                        tenantId,
                        transaction.transactionId(),
                        policy.matchingWindowStartingAt(transaction.recordedAt())));
            }
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        for (var transaction : internal) {
            var reconciliation = reconciliations
                    .findByTenantIdAndTransactionId(tenantId, transaction.transactionId())
                    .orElseThrow();
            if (reconciliation.status() != ReconciliationStatus.PENDING
                    || reconciliation.matchingWindow().isExpired(now)) {
                continue;
            }

            var sameType = external.stream()
                    .filter(candidate -> candidate.type().name().equals(transaction.type().name()))
                    .toList();
            var candidates = sameType.isEmpty() ? external : sameType;
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
                requireReview(
                        reconciliation, candidates, now, List.of(), List.of(discrepancy), correlationId, causationId);
                continue;
            }
            if (candidates.isEmpty()) {
                continue;
            }

            var providerTransaction = candidates.getFirst();
            var evaluations =
                    evaluator.evaluate(transaction, providerTransaction, policy.maximumTransactionTimeDifference());
            var discrepancies = discrepancyDetector.detect(evaluations, transaction.type(), now);
            if (!discrepancies.isEmpty()) {
                requireReview(reconciliation, candidates, now, evaluations, discrepancies, correlationId, causationId);
                continue;
            }

            var decision = new MatchDecision(tenantId, providerTransaction.providerTransactionId(), now, evaluations);
            var matched = reconciliations.save(reconciliation.match(decision));
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
    }

    private void requireReview(
            Reconciliation reconciliation,
            List<ProviderTransactionView> candidates,
            Instant now,
            List<RuleEvaluation> evaluations,
            List<Discrepancy> discrepancies,
            UUID correlationId,
            UUID causationId) {
        var decision = new ExceptionDecision(
                reconciliation.tenantId(),
                candidates.stream().map(ProviderTransactionView::providerTransactionId).toList(),
                now,
                evaluations,
                discrepancies);
        var review = reconciliations.save(reconciliation.requireReview(decision));
        events.publishEvent(new ReconciliationReviewRequired(
                UUID.randomUUID(),
                review.tenantId(),
                review.reconciliationId(),
                now,
                discrepancies,
                correlationId,
                causationId));
    }
}
