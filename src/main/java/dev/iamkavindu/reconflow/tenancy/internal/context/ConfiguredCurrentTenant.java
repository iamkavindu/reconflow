package dev.iamkavindu.reconflow.tenancy.internal.context;

import dev.iamkavindu.reconflow.tenancy.CurrentTenant;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ConfiguredCurrentTenant implements CurrentTenant {

    private final TenantId tenantId;

    ConfiguredCurrentTenant(@Value("${reconflow.tenancy.current-tenant-id}") UUID tenantId) {
        this.tenantId = new TenantId(tenantId);
    }

    @Override
    public TenantId tenantId() {
        return tenantId;
    }
}
