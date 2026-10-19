package dev.iamkavindu.reconflow.cases.internal.application;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.events.CaseOpened;
import dev.iamkavindu.reconflow.cases.internal.domain.Case;
import dev.iamkavindu.reconflow.cases.internal.domain.CaseRepository;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CaseOpeningService {

    private final CaseRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final ReconciliationQueries reconciliations;

    CaseOpeningService(
            CaseRepository repository,
            Clock clock,
            ApplicationEventPublisher events,
            ReconciliationQueries reconciliations) {
        this.repository = repository;
        this.clock = clock;
        this.events = events;
        this.reconciliations = reconciliations;
    }

    @Transactional
    public void open(
            TenantId tenantId,
            ReconciliationId reconciliationId,
            Instant decidedAt,
            UUID correlationId,
            UUID causationId) {
        var reconciliation = reconciliations.getReconciliationById(tenantId, reconciliationId);
        if (reconciliation.status() != ReconciliationStatus.REVIEW_REQUIRED
                && reconciliation.status() != ReconciliationStatus.UNMATCHED) {
            throw new IllegalStateException("only a reconciliation exception can open a case");
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (now.isBefore(decidedAt)) {
            now = decidedAt;
        }
        var pending = Case.open(new CaseId(UUID.randomUUID()), tenantId, reconciliationId, now);
        var result = repository.openIfAbsent(pending);
        if (result.created()) {
            var opened = result.investigationCase();
            events.publishEvent(new CaseOpened(
                    UUID.randomUUID(),
                    tenantId,
                    opened.caseId(),
                    reconciliationId,
                    opened.openedAt(),
                    correlationId,
                    causationId));
        }
    }
}
