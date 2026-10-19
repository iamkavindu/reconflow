package dev.iamkavindu.reconflow.ai;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;

public interface KnowledgeSearch {

    List<KnowledgeEvidence> search(TenantId tenantId, KnowledgeKind kind, String query);
}
