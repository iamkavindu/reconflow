package dev.iamkavindu.reconflow.ai.internal;

import dev.iamkavindu.reconflow.ai.KnowledgeEvidence;
import dev.iamkavindu.reconflow.ai.KnowledgeKind;
import dev.iamkavindu.reconflow.ai.KnowledgeSearch;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "reconflow.ai.knowledge-enabled", havingValue = "false", matchIfMissing = true)
class EmptyKnowledgeSearch implements KnowledgeSearch {

    @Override
    public List<KnowledgeEvidence> search(TenantId tenantId, KnowledgeKind kind, String query) {
        return List.of();
    }
}
