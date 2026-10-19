package dev.iamkavindu.reconflow.ai;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeDocument(UUID id, TenantId tenantId, KnowledgeKind kind, String title, String content,
        String importedBy, Instant importedAt, List<UUID> chunkIds) {

    public KnowledgeDocument {
        chunkIds = List.copyOf(chunkIds);
    }
}
