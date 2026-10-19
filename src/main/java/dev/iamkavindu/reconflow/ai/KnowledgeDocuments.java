package dev.iamkavindu.reconflow.ai;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.UUID;

public interface KnowledgeDocuments {
    boolean available();
    KnowledgeDocument importDocument(ImportKnowledgeDocument command);
    KnowledgeDocument get(TenantId tenantId, UUID id);
    KnowledgePassage passage(TenantId tenantId, UUID chunkId);
}
