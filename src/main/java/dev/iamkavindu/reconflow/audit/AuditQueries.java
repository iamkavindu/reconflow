package dev.iamkavindu.reconflow.audit;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;
import java.util.UUID;

public interface AuditQueries {

    List<AuditEntry> forResource(TenantId tenantId, AuditResourceType type, UUID resourceId, long before, int limit);
}
