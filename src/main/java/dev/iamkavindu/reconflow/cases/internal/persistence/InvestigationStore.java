package dev.iamkavindu.reconflow.cases.internal.persistence;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.InvestigationId;
import dev.iamkavindu.reconflow.cases.InvestigationResult;
import dev.iamkavindu.reconflow.cases.InvestigationStatus;
import dev.iamkavindu.reconflow.cases.internal.domain.Investigation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class InvestigationStore {

    private final InvestigationJpaRepository repository;
    private final JsonMapper json;

    public InvestigationStore(InvestigationJpaRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public boolean request(InvestigationId id, TenantId tenant, CaseId caseId, String actor, Instant now) {
        return repository.requestIfAbsent(id.value(), tenant.value(), caseId.value(), actor, now) == 1;
    }

    public Investigation get(TenantId tenant, InvestigationId id) {
        return repository.findByTenantIdAndId(tenant.value(), id.value()).map(this::map)
                .orElseThrow(() -> new NoSuchElementException("Investigation not found"));
    }

    public Investigation activeOrLatest(TenantId tenant, CaseId caseId) {
        return repository.findFirstByTenantIdAndCaseIdAndStatusIn(tenant.value(), caseId.value(),
                List.of(InvestigationStatus.REQUESTED, InvestigationStatus.RUNNING)).map(this::map)
                .orElseGet(() -> history(tenant, caseId).getFirst());
    }

    public List<Investigation> history(TenantId tenant, CaseId caseId) {
        return repository.findTop100ByTenantIdAndCaseIdOrderByRequestedAtDescIdDesc(tenant.value(), caseId.value())
                .stream().map(this::map).toList();
    }

    public Investigation save(Investigation value) {
        var entity = new InvestigationJpaEntity();
        entity.id = value.id().value();
        entity.tenantId = value.tenantId().value();
        entity.caseId = value.caseId().value();
        entity.status = value.status();
        entity.requestedBy = value.requestedBy();
        entity.requestedAt = value.requestedAt();
        entity.startedAt = value.startedAt();
        entity.completedAt = value.completedAt();
        entity.result = value.result() == null ? null : json.writeValueAsString(value.result());
        entity.failureReason = value.failureReason();
        entity.model = value.model();
        entity.promptVersion = value.promptVersion();
        entity.version = value.version();
        return map(repository.saveAndFlush(entity));
    }

    private Investigation map(InvestigationJpaEntity entity) {
        return new Investigation(new InvestigationId(entity.id), new TenantId(entity.tenantId), new CaseId(entity.caseId),
                entity.status, entity.requestedBy, entity.requestedAt, entity.startedAt, entity.completedAt,
                entity.result == null ? null : json.readValue(entity.result, InvestigationResult.class),
                entity.failureReason, entity.model, entity.promptVersion, entity.version);
    }
}
