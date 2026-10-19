package dev.iamkavindu.reconflow.cases.events;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

public record CaseReviewStarted(UUID eventId, TenantId tenantId, CaseId caseId, String actor, Instant startedAt) {}
