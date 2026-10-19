package dev.iamkavindu.reconflow.cases.internal.domain;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseNote;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.cases.Resolution;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record Case(
        CaseId caseId,
        TenantId tenantId,
        ReconciliationId reconciliationId,
        CaseStatus status,
        Instant openedAt,
        Instant updatedAt,
        List<CaseNote> notes,
        Resolution resolution,
        long version) {

    public Case {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(reconciliationId, "reconciliationId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        notes = List.copyOf(notes);
        if ((status == CaseStatus.RESOLVED) != (resolution != null)) {
            throw new IllegalArgumentException("only a resolved case must contain a resolution");
        }
        if (updatedAt.isBefore(openedAt) || version < 0) {
            throw new IllegalArgumentException("case timestamps and version must be valid");
        }
    }

    public static Case open(CaseId id, TenantId tenantId, ReconciliationId reconciliationId, Instant now) {
        return new Case(id, tenantId, reconciliationId, CaseStatus.OPEN, now, now, List.of(), null, 0);
    }

    public Case startReview(Instant now) {
        requireMutable(now);
        if (status != CaseStatus.OPEN) {
            throw new IllegalStateException("only an open case can start review");
        }
        return new Case(
                caseId, tenantId, reconciliationId, CaseStatus.UNDER_REVIEW, openedAt, now, notes, null, version);
    }

    public Case addNote(CaseNote note) {
        requireMutable(note.addedAt());
        var updatedNotes = new ArrayList<>(notes);
        updatedNotes.add(note);
        return new Case(
                caseId, tenantId, reconciliationId, status, openedAt, note.addedAt(), updatedNotes, null, version);
    }

    public Case resolve(Resolution resolution) {
        requireMutable(resolution.resolvedAt());
        return new Case(
                caseId,
                tenantId,
                reconciliationId,
                CaseStatus.RESOLVED,
                openedAt,
                resolution.resolvedAt(),
                notes,
                resolution,
                version);
    }

    private void requireMutable(Instant now) {
        Objects.requireNonNull(now, "operation timestamp must not be null");
        if (status == CaseStatus.RESOLVED) {
            throw new IllegalStateException("a resolved case cannot be changed");
        }
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("case history cannot move backwards in time");
        }
    }
}
