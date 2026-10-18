package dev.iamkavindu.reconflow.audit.internal;

import dev.iamkavindu.reconflow.audit.AuditEntry;
import dev.iamkavindu.reconflow.audit.AuditQueries;
import dev.iamkavindu.reconflow.audit.AuditResourceType;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SqlAuditQueries implements AuditQueries {

    private final JdbcClient jdbc;

    SqlAuditQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> forResource(
            TenantId tenantId, AuditResourceType type, UUID resourceId, long before, int limit) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(resourceId);
        if (before <= 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("history requires a positive cursor and a limit between 1 and 100");
        }
        return jdbc.sql("""
                        SELECT * FROM audit.audit_entries
                        WHERE tenant_id = :tenant AND resource_type = :type AND resource_id = :resource
                          AND sequence < :before
                        ORDER BY sequence DESC LIMIT :limit
                        """)
                .param("tenant", tenantId.value())
                .param("type", type.name())
                .param("resource", resourceId)
                .param("before", before)
                .param("limit", limit)
                .query((rs, row) -> new AuditEntry(
                        rs.getLong("sequence"),
                        rs.getObject("event_id", UUID.class),
                        tenantId,
                        type,
                        resourceId,
                        rs.getString("action"),
                        rs.getString("actor"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getTimestamp("recorded_at").toInstant(),
                        rs.getObject("correlation_id", UUID.class),
                        rs.getObject("causation_id", UUID.class)))
                .list();
    }
}
