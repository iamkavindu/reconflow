package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.tenancy.TenantId;

public interface CandidateLock {

    void acquire(TenantId tenantId, String businessReference);
}
