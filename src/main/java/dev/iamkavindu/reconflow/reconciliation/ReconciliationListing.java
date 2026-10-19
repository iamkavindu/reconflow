package dev.iamkavindu.reconflow.reconciliation;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReconciliationListing {
    List<ReconciliationView> search(TenantId tenantId, Filter filter);

    Map<ReconciliationStatus, Long> counts(TenantId tenantId);

    record Filter(
            UUID reconciliationId,
            UUID transactionId,
            UUID providerTransactionId,
            String reference,
            ReconciliationStatus status,
            Instant from,
            Instant to,
            int limit,
            int offset) {
        public Filter {
            if (limit < 1
                    || limit > 100
                    || offset < 0
                    || offset > 10000
                    || (reference != null && (reference.isBlank() || reference.length() > 100))
                    || (from != null && to != null && !from.isBefore(to))) {
                throw new IllegalArgumentException("invalid reconciliation search bounds");
            }
        }
    }
}
