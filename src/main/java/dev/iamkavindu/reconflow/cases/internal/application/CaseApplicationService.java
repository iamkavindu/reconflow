package dev.iamkavindu.reconflow.cases.internal.application;

import dev.iamkavindu.reconflow.cases.CaseCommands;
import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseNote;
import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.cases.CaseView;
import dev.iamkavindu.reconflow.cases.Resolution;
import dev.iamkavindu.reconflow.cases.ResolutionType;
import dev.iamkavindu.reconflow.cases.events.CaseNoteAdded;
import dev.iamkavindu.reconflow.cases.events.CaseResolved;
import dev.iamkavindu.reconflow.cases.events.CaseReviewStarted;
import dev.iamkavindu.reconflow.cases.internal.domain.Case;
import dev.iamkavindu.reconflow.cases.internal.domain.CaseRepository;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CaseApplicationService implements CaseCommands, CaseQueries {

    private final CaseRepository repository;
    private final CurrentActor currentActor;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    CaseApplicationService(
            CaseRepository repository, CurrentActor currentActor, Clock clock, ApplicationEventPublisher events) {
        this.repository = repository;
        this.currentActor = currentActor;
        this.clock = clock;
        this.events = events;
    }

    @Override
    @Transactional
    public CaseView startReview(CaseId caseId) {
        Actor actor = analyst();
        var saved = repository.save(load(actor.tenantId(), caseId).startReview(now()));
        events.publishEvent(new CaseReviewStarted(
                UUID.randomUUID(), actor.tenantId(), saved.caseId(), actor.subject(), saved.updatedAt()));
        return toView(saved);
    }

    @Override
    @Transactional
    public CaseView addNote(CaseId caseId, String text) {
        Actor actor = analyst();
        var note = new CaseNote(UUID.randomUUID(), text, actor.subject(), now());
        var saved = repository.save(load(actor.tenantId(), caseId).addNote(note));
        events.publishEvent(new CaseNoteAdded(UUID.randomUUID(), actor.tenantId(), saved.caseId(), note));
        return toView(saved);
    }

    @Override
    @Transactional
    public CaseView resolveCase(CaseId caseId, ResolutionType type, String reason, List<String> evidenceReferences) {
        Actor actor = analyst();
        var resolution = new Resolution(type, reason, actor.subject(), now(), evidenceReferences);
        var saved = repository.save(load(actor.tenantId(), caseId).resolve(resolution));
        events.publishEvent(new CaseResolved(UUID.randomUUID(), actor.tenantId(), saved.caseId(), resolution));
        return toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CaseView getCase(TenantId tenantId, CaseId caseId) {
        return toView(load(tenantId, caseId));
    }

    @Override
    @Transactional(readOnly = true)
    public CaseView getCaseForReconciliation(TenantId tenantId, ReconciliationId reconciliationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(reconciliationId, "reconciliationId must not be null");
        return repository
                .findByTenantIdAndReconciliationId(tenantId, reconciliationId)
                .map(CaseApplicationService::toView)
                .orElseThrow(() -> new NoSuchElementException("Case not found"));
    }

    private Case load(TenantId tenantId, CaseId caseId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        return repository
                .findByTenantIdAndId(tenantId, caseId)
                .orElseThrow(() -> new NoSuchElementException("Case not found"));
    }

    private Actor analyst() {
        Actor actor = currentActor.actor();
        actor.requireAnalyst();
        return actor;
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static CaseView toView(Case investigationCase) {
        return new CaseView(
                investigationCase.caseId(),
                investigationCase.tenantId(),
                investigationCase.reconciliationId(),
                investigationCase.status(),
                investigationCase.openedAt(),
                investigationCase.updatedAt(),
                investigationCase.notes(),
                investigationCase.resolution());
    }
}
