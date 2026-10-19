package dev.iamkavindu.reconflow.reporting;

import dev.iamkavindu.reconflow.tenancy.TenantId;

public interface DashboardQueries {
    Dashboard get(TenantId tenantId);
}
