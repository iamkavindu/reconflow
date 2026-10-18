package dev.iamkavindu.reconflow.cases;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;

public record CaseView(
        CaseId caseId,
        TenantId tenantId,
        ReconciliationId reconciliationId,
        CaseStatus status,
        Instant openedAt,
        Instant updatedAt,
        List<CaseNote> notes,
        Resolution resolution) {

    public CaseView {
        notes = List.copyOf(notes);
    }
}
