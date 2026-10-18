package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.provider.ProviderTransactionCandidates;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationMatched;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.MatchDecision;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationEvaluator;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationRepository;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionCandidates;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

        if (internal.size() != 1 || external.size() != 1) {
            return;
        }

        var transaction = internal.getFirst();
        var providerTransaction = external.getFirst();
        var reconciliation = reconciliations
                .findByTenantIdAndTransactionId(tenantId, transaction.transactionId())
                .orElseThrow();
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (reconciliation.status() != ReconciliationStatus.PENDING
                || reconciliation.matchingWindow().isExpired(now)) {
            return;
        }

        var evaluations = evaluator.evaluate(transaction, providerTransaction, policy.maximumTransactionTimeDifference());
        if (evaluations.stream().anyMatch(evaluation -> !evaluation.satisfied())) {
            return;
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
