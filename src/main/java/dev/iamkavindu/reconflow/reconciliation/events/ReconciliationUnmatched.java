package dev.iamkavindu.reconflow.reconciliation.events;

import dev.iamkavindu.reconflow.reconciliation.Discrepancy;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationUnmatched(
        UUID eventId,
        TenantId tenantId,
        ReconciliationId reconciliationId,
        Instant decidedAt,
        List<Discrepancy> discrepancies,
        UUID correlationId,
        UUID causationId) {

    public ReconciliationUnmatched {
        discrepancies = List.copyOf(discrepancies);
    }
}
