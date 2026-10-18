package dev.iamkavindu.reconflow.tenancy.internal.context;

import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.tenancy.UserRole;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class TenantMemberships {

    private final JdbcClient jdbc;

    TenantMemberships(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Actor> findEnabled(TenantId tenantId, String subject) {
        return jdbc.sql("""
                        SELECT role FROM tenancy.tenant_memberships
                        WHERE tenant_id = :tenant AND oidc_subject = :subject AND enabled
                        """)
                .param("tenant", tenantId.value())
                .param("subject", subject)
                .query((rs, row) -> new Actor(tenantId, subject, UserRole.valueOf(rs.getString("role"))))
                .optional();
    }
}
