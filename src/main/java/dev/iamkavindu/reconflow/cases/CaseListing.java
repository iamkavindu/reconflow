package dev.iamkavindu.reconflow.cases;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CaseListing {
    List<CaseView> search(TenantId tenantId, Filter filter);

    Map<CaseStatus, Long> counts(TenantId tenantId);

    record Filter(
            UUID caseId, UUID reconciliationId, CaseStatus status, Instant from, Instant to, int limit, int offset) {
        public Filter {
            if (limit < 1
                    || limit > 100
                    || offset < 0
                    || offset > 10000
                    || (from != null && to != null && !from.isBefore(to))) {
                throw new IllegalArgumentException("invalid case search bounds");
            }
        }
    }
}
