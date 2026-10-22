package dev.iamkavindu.reconflow;

import dev.iamkavindu.reconflow.ai.InvestigationEngine;
import dev.iamkavindu.reconflow.ai.InvestigationFailure;
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
        long started = System.nanoTime();
        var running = processing.start(event.tenantId(), event.investigationId());
        if (running.status() != InvestigationStatus.RUNNING) {
            return;
        }
        try {
            var result = engine.investigate(event.tenantId(), event.caseId());
            processing.complete(
                    event.tenantId(), event.investigationId(), result, engine.model(), engine.promptVersion());
        } catch (RuntimeException error) {
            var failure = error instanceof InvestigationFailure diagnostic ? diagnostic : null;
            LOG.atWarn()
                    .addKeyValue("investigation_id", event.investigationId().value())
                    .addKeyValue("tenant_id", event.tenantId().value())
                    .addKeyValue("workflow_stage", failure == null ? "COMPLETION" : failure.stage())
                    .addKeyValue("error_category", failure == null ? "WORKFLOW_FAILURE" : failure.category())
                    .addKeyValue("exception_class", failure == null ? error.getClass().getName() : failure.exceptionClass())
                    .addKeyValue("cause_class", failure == null ? error.getClass().getName() : failure.causeClass())
                    .addKeyValue("request_timeout", failure == null || failure.requestTimeout() == null
                            ? "dependency-specific" : failure.requestTimeout())
                    .addKeyValue("elapsed_ms", failure == null ? (System.nanoTime() - started) / 1_000_000 : failure.elapsedMs())
                    .addKeyValue("total_elapsed_ms", (System.nanoTime() - started) / 1_000_000)
                    .addKeyValue("model", engine.model())
                    .log("Investigation {} failed workflow_stage={} error_category={} exception_class={} "
                                    + "cause_class={} request_timeout={} elapsed_ms={} model={}",
                            event.investigationId().value(),
                            failure == null ? "COMPLETION" : failure.stage(),
                            failure == null ? "WORKFLOW_FAILURE" : failure.category(),
                            failure == null ? error.getClass().getName() : failure.exceptionClass(),
                            failure == null ? error.getClass().getName() : failure.causeClass(),
                            failure == null || failure.requestTimeout() == null ? "dependency-specific" : failure.requestTimeout(),
                            failure == null ? (System.nanoTime() - started) / 1_000_000 : failure.elapsedMs(),
                            engine.model());
            processing.fail(
                    event.tenantId(),
                    event.investigationId(),
                    "AI investigation could not produce a supported recommendation. Human review is required.");
        }
    }
}
