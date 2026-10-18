package dev.iamkavindu.reconflow.audit.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface AuditRepository extends Repository<AuditJpaEntity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO audit.audit_entries
              (event_id, tenant_id, resource_type, resource_id, action, actor,
               occurred_at, recorded_at, correlation_id, causation_id)
            VALUES (:event, :tenant, :type, :resource, :action, :actor, :occurred, :recorded, :correlation, :causation)
            ON CONFLICT (tenant_id, event_id) DO NOTHING
            """, nativeQuery = true)
    int append(
            @Param("event") UUID eventId,
            @Param("tenant") UUID tenantId,
            @Param("type") String resourceType,
            @Param("resource") UUID resourceId,
            @Param("action") String action,
            @Param("actor") String actor,
            @Param("occurred") Instant occurredAt,
            @Param("recorded") Instant recordedAt,
            @Param("correlation") UUID correlationId,
            @Param("causation") UUID causationId);
}
