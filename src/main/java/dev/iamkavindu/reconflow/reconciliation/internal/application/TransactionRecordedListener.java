package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.transactions.events.TransactionRecorded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class TransactionRecordedListener {

    private final AutomaticReconciliation reconciliation;

    TransactionRecordedListener(AutomaticReconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    @ApplicationModuleListener
    void on(TransactionRecorded event) {
        reconciliation.attempt(event.tenantId(), event.businessReference(), event.correlationId(), event.eventId());
    }
}
