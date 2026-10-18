package dev.iamkavindu.reconflow.cases.internal.persistence;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.internal.domain.Case;
import dev.iamkavindu.reconflow.cases.internal.domain.CaseRepository;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaCaseRepository implements CaseRepository {

    private final SpringDataCaseRepository repository;

    JpaCaseRepository(SpringDataCaseRepository repository) {
        this.repository = repository;
    }

    @Override
    public OpenResult openIfAbsent(Case investigationCase) {
        int inserted = repository.openIfAbsent(
                investigationCase.caseId().value(),
                investigationCase.tenantId().value(),
                investigationCase.reconciliationId().value(),
                investigationCase.openedAt());
        var existing = findByTenantIdAndReconciliationId(
                        investigationCase.tenantId(), investigationCase.reconciliationId())
                .orElseThrow();
        return new OpenResult(existing, inserted == 1);
    }

    @Override
    public Case save(Case investigationCase) {
        var saved = repository.saveAndFlush(CasePersistenceMapper.toEntity(investigationCase));
        return CasePersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Case> findByTenantIdAndId(TenantId tenantId, CaseId caseId) {
        return repository
                .findByTenantIdAndId(tenantId.value(), caseId.value())
                .map(CasePersistenceMapper::toDomain);
    }

    @Override
    public Optional<Case> findByTenantIdAndReconciliationId(TenantId tenantId, ReconciliationId reconciliationId) {
        return repository
                .findByTenantIdAndReconciliationId(tenantId.value(), reconciliationId.value())
                .map(CasePersistenceMapper::toDomain);
    }
}
