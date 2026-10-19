package dev.iamkavindu.reconflow.cases.internal.persistence;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseNote;
import dev.iamkavindu.reconflow.cases.Resolution;
import dev.iamkavindu.reconflow.cases.internal.domain.Case;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.Arrays;
import tools.jackson.databind.json.JsonMapper;

final class CasePersistenceMapper {

    private static final JsonMapper JSON =
            JsonMapper.builder().findAndAddModules().build();

    private CasePersistenceMapper() {}

    static CaseJpaEntity toEntity(Case investigationCase) {
        var resolution = investigationCase.resolution();
        return new CaseJpaEntity(
                investigationCase.caseId().value(),
                investigationCase.tenantId().value(),
                investigationCase.reconciliationId().value(),
                investigationCase.status(),
                investigationCase.openedAt(),
                investigationCase.updatedAt(),
                JSON.writeValueAsString(investigationCase.notes()),
                resolution == null ? null : JSON.writeValueAsString(resolution),
                investigationCase.version());
    }

    static Case toDomain(CaseJpaEntity entity) {
        return new Case(
                new CaseId(entity.id),
                new TenantId(entity.tenantId),
                new ReconciliationId(entity.reconciliationId),
                entity.status,
                entity.openedAt,
                entity.updatedAt,
                Arrays.asList(JSON.readValue(entity.notes, CaseNote[].class)),
                entity.resolution == null ? null : JSON.readValue(entity.resolution, Resolution.class),
                entity.version);
    }
}
