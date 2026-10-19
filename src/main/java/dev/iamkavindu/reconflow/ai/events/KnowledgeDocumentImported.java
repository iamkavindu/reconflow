package dev.iamkavindu.reconflow.ai.events;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentImported(
        UUID eventId, TenantId tenantId, UUID documentId, String actor, Instant importedAt) {}
