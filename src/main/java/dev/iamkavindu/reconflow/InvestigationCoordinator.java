package dev.iamkavindu.reconflow;

import dev.iamkavindu.reconflow.ai.InvestigationEngine;
import dev.iamkavindu.reconflow.cases.InvestigationProcessing;
import dev.iamkavindu.reconflow.cases.InvestigationStatus;
import dev.iamkavindu.reconflow.cases.events.InvestigationRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "reconflow.ai.enabled", havingValue = "true")
class InvestigationCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(InvestigationCoordinator.class);
    private final InvestigationProcessing processing;
    private final InvestigationEngine engine;

    InvestigationCoordinator(InvestigationProcessing processing, InvestigationEngine engine) {
        this.processing = processing;
        this.engine = engine;
    }

    @Async("investigationExecutor")
    @TransactionalEventListener
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void on(InvestigationRequested event) {
        var running = processing.start(event.tenantId(), event.investigationId());
        if (running.status() != InvestigationStatus.RUNNING) {
            return;
        }
        try {
            var result = engine.investigate(event.tenantId(), event.caseId());
            processing.complete(event.tenantId(), event.investigationId(), result, engine.model(), engine.promptVersion());
        } catch (RuntimeException error) {
            LOG.warn("Investigation {} failed for tenant {} ({})", event.investigationId().value(), event.tenantId().value(),
                    error.getClass().getSimpleName());
            processing.fail(event.tenantId(), event.investigationId(),
                    "AI investigation could not produce a supported recommendation. Human review is required.");
        }
    }
}
