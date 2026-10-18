package dev.iamkavindu.reconflow.cases.internal.application;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.cases.InvestigationCommands;
import dev.iamkavindu.reconflow.cases.InvestigationId;
import dev.iamkavindu.reconflow.cases.InvestigationProcessing;
import dev.iamkavindu.reconflow.cases.InvestigationQueries;
import dev.iamkavindu.reconflow.cases.InvestigationResult;
import dev.iamkavindu.reconflow.cases.InvestigationView;
import dev.iamkavindu.reconflow.cases.events.InvestigationRequested;
import dev.iamkavindu.reconflow.cases.events.InvestigationStateChanged;
import dev.iamkavindu.reconflow.cases.internal.domain.Investigation;
import dev.iamkavindu.reconflow.cases.internal.persistence.InvestigationStore;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InvestigationApplicationService implements InvestigationCommands, InvestigationQueries, InvestigationProcessing {

    private final InvestigationStore store;
    private final CaseQueries cases;
    private final CurrentActor currentActor;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    InvestigationApplicationService(InvestigationStore store, CaseQueries cases, CurrentActor currentActor,
            Clock clock, ApplicationEventPublisher events) {
        this.store = store;
        this.cases = cases;
        this.currentActor = currentActor;
        this.clock = clock;
        this.events = events;
    }

    @Override
    @Transactional
    public InvestigationView request(CaseId caseId) {
        var actor = currentActor.actor();
        actor.requireAnalyst();
        var investigationCase = cases.getCase(actor.tenantId(), caseId);
        if (investigationCase.status() == CaseStatus.RESOLVED) {
            throw new IllegalStateException("a resolved case cannot start another investigation");
        }
        Instant now = now();
        var id = new InvestigationId(UUID.randomUUID());
        if (!store.request(id, actor.tenantId(), caseId, actor.subject(), now)) {
            return view(store.activeOrLatest(actor.tenantId(), caseId));
        }
        // The request event identity is deterministic for this investigation.
        events.publishEvent(new InvestigationRequested(id.value(), actor.tenantId(), caseId, id,
                actor.subject(), now, id.value()));
        return view(store.get(actor.tenantId(), id));
    }

    @Override
    @Transactional(readOnly = true)
    public InvestigationView get(TenantId tenantId, InvestigationId investigationId) {
        return view(store.get(tenantId, investigationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvestigationView> forCase(TenantId tenantId, CaseId caseId) {
        cases.getCase(tenantId, caseId);
        return store.history(tenantId, caseId).stream().map(InvestigationApplicationService::view).toList();
    }

    @Override
    @Transactional
    public InvestigationView start(TenantId tenantId, InvestigationId investigationId) {
        var existing = store.get(tenantId, investigationId);
        var started = existing.start(now());
        return changed(existing, started);
    }

    @Override
    @Transactional
    public InvestigationView complete(TenantId tenantId, InvestigationId investigationId,
            InvestigationResult result, String model, String promptVersion) {
        var existing = store.get(tenantId, investigationId);
        return changed(existing, existing.complete(result, model, promptVersion, now()));
    }

    @Override
    @Transactional
    public InvestigationView fail(TenantId tenantId, InvestigationId investigationId, String safeReason) {
        var existing = store.get(tenantId, investigationId);
        return changed(existing, existing.fail(safeReason, now()));
    }

    private InvestigationView changed(Investigation existing, Investigation next) {
        if (existing == next) {
            return view(existing);
        }
        var saved = store.save(next);
        Instant occurredAt = saved.completedAt() == null ? saved.startedAt() : saved.completedAt();
        events.publishEvent(new InvestigationStateChanged(UUID.randomUUID(), saved.tenantId(), saved.id(),
                saved.status(), occurredAt, saved.id().value(), saved.id().value()));
        return view(saved);
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static InvestigationView view(Investigation value) {
        return new InvestigationView(value.id(), value.tenantId(), value.caseId(), "AI_ASSISTED", value.status(), value.requestedBy(),
                value.requestedAt(), value.startedAt(), value.completedAt(), value.result(), value.failureReason(),
                value.model(), value.promptVersion());
    }
}
