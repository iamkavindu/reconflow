package dev.iamkavindu.reconflow.cases.internal.domain;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.InvestigationId;
import dev.iamkavindu.reconflow.cases.InvestigationResult;
import dev.iamkavindu.reconflow.cases.InvestigationStatus;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;

public record Investigation(
        InvestigationId id,
        TenantId tenantId,
        CaseId caseId,
        InvestigationStatus status,
        String requestedBy,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        InvestigationResult result,
        String failureReason,
        String model,
        String promptVersion,
        long version) {

    public Investigation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(requestedBy);
        Objects.requireNonNull(requestedAt);
        boolean terminal = status == InvestigationStatus.COMPLETED || status == InvestigationStatus.FAILED;
        if (requestedBy.isBlank()
                || requestedBy.length() > 255
                || version < 0
                || terminal != (completedAt != null)
                || (status == InvestigationStatus.REQUESTED && startedAt != null)
                || (status != InvestigationStatus.REQUESTED && startedAt == null)
                || (status == InvestigationStatus.COMPLETED) != (result != null)
                || (status == InvestigationStatus.FAILED) != (failureReason != null)
                || (failureReason != null && (failureReason.isBlank() || failureReason.length() > 500))
                || (result != null
                        && (!result.judgment().accepted()
                                || model == null
                                || model.isBlank()
                                || model.length() > 200
                                || promptVersion == null
                                || promptVersion.isBlank()
                                || promptVersion.length() > 100))
                || (startedAt != null && startedAt.isBefore(requestedAt))
                || (completedAt != null && completedAt.isBefore(startedAt))) {
            throw new IllegalArgumentException("invalid investigation lifecycle state");
        }
    }

    public static Investigation request(InvestigationId id, TenantId tenant, CaseId caseId, String actor, Instant now) {
        return new Investigation(
                id, tenant, caseId, InvestigationStatus.REQUESTED, actor, now, null, null, null, null, null, null, 0);
    }

    public boolean terminal() {
        return status == InvestigationStatus.COMPLETED || status == InvestigationStatus.FAILED;
    }

    public Investigation start(Instant now) {
        if (status != InvestigationStatus.REQUESTED) {
            return this;
        }
        return new Investigation(
                id,
                tenantId,
                caseId,
                InvestigationStatus.RUNNING,
                requestedBy,
                requestedAt,
                now,
                null,
                null,
                null,
                null,
                null,
                version);
    }

    public Investigation complete(InvestigationResult result, String model, String promptVersion, Instant now) {
        if (terminal()) {
            return this;
        }
        if (status != InvestigationStatus.RUNNING
                || !result.judgment().accepted()
                || model == null
                || model.isBlank()
                || promptVersion == null
                || promptVersion.isBlank()) {
            throw new IllegalStateException("a running investigation needs an accepted, attributable recommendation");
        }
        return new Investigation(
                id,
                tenantId,
                caseId,
                InvestigationStatus.COMPLETED,
                requestedBy,
                requestedAt,
                startedAt,
                now,
                result,
                null,
                model,
                promptVersion,
                version);
    }

    public Investigation fail(String safeReason, Instant now) {
        if (terminal()) {
            return this;
        }
        if (safeReason == null || safeReason.isBlank() || safeReason.length() > 500) {
            throw new IllegalArgumentException("a bounded failure reason is required");
        }
        return new Investigation(
                id,
                tenantId,
                caseId,
                InvestigationStatus.FAILED,
                requestedBy,
                requestedAt,
                startedAt == null ? now : startedAt,
                now,
                null,
                safeReason,
                null,
                null,
                version);
    }
}
