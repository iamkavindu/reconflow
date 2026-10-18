package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.Reconciliation;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationRepository;
import dev.iamkavindu.reconflow.transactions.events.TransactionRecorded;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class TransactionRecordedListener {

    private final ReconciliationRepository reconciliationRepository;
    private final ReconciliationPolicy policy;

    TransactionRecordedListener(ReconciliationRepository reconciliationRepository, ReconciliationPolicy policy) {
        this.reconciliationRepository = reconciliationRepository;
        this.policy = policy;
    }

    @ApplicationModuleListener
    void on(TransactionRecorded event) {
        if (reconciliationRepository.existsByTenantIdAndTransactionId(event.tenantId(), event.transactionId())) {
            return;
        }

        reconciliationRepository.save(Reconciliation.pending(
                new ReconciliationId(UUID.randomUUID()),
                event.tenantId(),
                event.transactionId(),
                policy.matchingWindowStartingAt(event.recordedAt())));
    }
}
