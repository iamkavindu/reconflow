package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.provider.events.ProviderTransactionRecorded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class ProviderTransactionRecordedListener {

    private final AutomaticReconciliation reconciliation;

    ProviderTransactionRecordedListener(AutomaticReconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    @ApplicationModuleListener
    void on(ProviderTransactionRecorded event) {
        reconciliation.attempt(event.tenantId(), event.businessReference(), event.correlationId(), event.eventId());
    }
}
