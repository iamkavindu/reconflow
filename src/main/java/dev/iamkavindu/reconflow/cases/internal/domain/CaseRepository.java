package dev.iamkavindu.reconflow.cases.internal.domain;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.Optional;

public interface CaseRepository {

    OpenResult openIfAbsent(Case investigationCase);

    Case save(Case investigationCase);

    Optional<Case> findByTenantIdAndId(TenantId tenantId, CaseId caseId);

    Optional<Case> findByTenantIdAndReconciliationId(TenantId tenantId, ReconciliationId reconciliationId);

    record OpenResult(Case investigationCase, boolean created) {}
}
