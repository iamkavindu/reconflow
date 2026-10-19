package dev.iamkavindu.reconflow.audit.internal;

import dev.iamkavindu.reconflow.audit.AuditResourceType;
import dev.iamkavindu.reconflow.ai.events.KnowledgeDocumentImported;
import dev.iamkavindu.reconflow.cases.events.CaseNoteAdded;
import dev.iamkavindu.reconflow.cases.events.CaseOpened;
import dev.iamkavindu.reconflow.cases.events.CaseResolved;
import dev.iamkavindu.reconflow.cases.events.CaseReviewStarted;
import dev.iamkavindu.reconflow.cases.events.InvestigationRequested;
import dev.iamkavindu.reconflow.cases.events.InvestigationStateChanged;
import dev.iamkavindu.reconflow.provider.events.ProviderTransactionRecorded;
import dev.iamkavindu.reconflow.provider.events.SettlementImported;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationMatched;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationReviewRequired;
import dev.iamkavindu.reconflow.reconciliation.events.ReconciliationUnmatched;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.events.TransactionRecorded;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class AuditEventListener {

    private final AuditRepository repository;
    private final Clock clock;

    AuditEventListener(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(KnowledgeDocumentImported event) {
        append(event.eventId(), event.tenantId(), AuditResourceType.KNOWLEDGE_DOCUMENT, event.documentId(),
                "KNOWLEDGE_DOCUMENT_IMPORTED", event.actor(), event.importedAt(), null, null);
    }

    @ApplicationModuleListener
    void on(TransactionRecorded event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.TRANSACTION,
                event.transactionId().value(),
                "TRANSACTION_RECORDED",
                event.recordedBy(),
                event.recordedAt(),
                event.correlationId(),
                null);
    }

    @ApplicationModuleListener
    void on(ProviderTransactionRecorded event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.PROVIDER_TRANSACTION,
                event.providerTransactionId().value(),
                "PROVIDER_TRANSACTION_RECORDED",
                null,
                event.recordedAt(),
                event.correlationId(),
                event.causationId());
    }

    @ApplicationModuleListener
    void on(SettlementImported event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.SETTLEMENT,
                event.settlementId().value(),
                "SETTLEMENT_IMPORTED",
                event.recordedBy(),
                event.recordedAt(),
                event.correlationId(),
                null);
    }

    @ApplicationModuleListener
    void on(ReconciliationMatched event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.RECONCILIATION,
                event.reconciliationId().value(),
                "RECONCILIATION_MATCHED",
                null,
                event.matchedAt(),
                event.correlationId(),
                event.causationId());
    }

    @ApplicationModuleListener
    void on(ReconciliationReviewRequired event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.RECONCILIATION,
                event.reconciliationId().value(),
                "RECONCILIATION_REVIEW_REQUIRED",
                null,
                event.decidedAt(),
                event.correlationId(),
                event.causationId());
    }

    @ApplicationModuleListener
    void on(ReconciliationUnmatched event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.RECONCILIATION,
                event.reconciliationId().value(),
                "RECONCILIATION_UNMATCHED",
                null,
                event.decidedAt(),
                event.correlationId(),
                event.causationId());
    }

    @ApplicationModuleListener
    void on(CaseOpened event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.CASE,
                event.caseId().value(),
                "CASE_OPENED",
                null,
                event.openedAt(),
                event.correlationId(),
                event.causationId());
    }

    @ApplicationModuleListener
    void on(CaseReviewStarted event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.CASE,
                event.caseId().value(),
                "CASE_REVIEW_STARTED",
                event.actor(),
                event.startedAt(),
                null,
                null);
    }

    @ApplicationModuleListener
    void on(CaseNoteAdded event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.CASE,
                event.caseId().value(),
                "CASE_NOTE_ADDED",
                event.note().addedBy(),
                event.note().addedAt(),
                null,
                null);
    }

    @ApplicationModuleListener
    void on(CaseResolved event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.CASE,
                event.caseId().value(),
                "CASE_RESOLVED",
                event.resolution().resolvedBy(),
                event.resolution().resolvedAt(),
                null,
                null);
    }

    private void append(
            UUID eventId,
            TenantId tenantId,
            AuditResourceType type,
            UUID resourceId,
            String action,
            String actor,
            Instant occurredAt,
            UUID correlationId,
            UUID causationId) {
        repository.append(
                eventId,
                tenantId.value(),
                type.name(),
                resourceId,
                action,
                actor,
                occurredAt,
                Instant.now(clock),
                correlationId,
                causationId);
    }

    @ApplicationModuleListener
    void on(InvestigationRequested event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.INVESTIGATION,
                event.investigationId().value(),
                "INVESTIGATION_REQUESTED",
                event.requestedBy(),
                event.requestedAt(),
                event.correlationId(),
                null);
    }

    @ApplicationModuleListener
    void on(InvestigationStateChanged event) {
        append(
                event.eventId(),
                event.tenantId(),
                AuditResourceType.INVESTIGATION,
                event.investigationId().value(),
                "INVESTIGATION_" + event.status().name(),
                null,
                event.changedAt(),
                event.correlationId(),
                event.causationId());
    }
}
