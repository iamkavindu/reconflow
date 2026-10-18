package dev.iamkavindu.reconflow.cases.internal.application;

import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationReviewRequired;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationUnmatched;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class ReconciliationExceptionListener {

    private final CaseOpeningService opening;

    ReconciliationExceptionListener(CaseOpeningService opening) {
        this.opening = opening;
    }

    @ApplicationModuleListener
    void on(ReconciliationReviewRequired event) {
        opening.open(
                event.tenantId(), event.reconciliationId(), event.decidedAt(), event.correlationId(), event.eventId());
    }

    @ApplicationModuleListener
    void on(ReconciliationUnmatched event) {
        opening.open(
                event.tenantId(), event.reconciliationId(), event.decidedAt(), event.correlationId(), event.eventId());
    }
}
