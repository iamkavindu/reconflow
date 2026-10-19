package dev.iamkavindu.reconflow.transactions;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransactionListing {
    List<TransactionView> search(TenantId tenantId, Filter filter);
    long count(TenantId tenantId);

    record Filter(UUID transactionId, String reference, Instant from, Instant to, int limit, int offset) {
        public Filter {
            if (limit < 1 || limit > 100 || offset < 0 || offset > 10000
                    || (reference != null && (reference.isBlank() || reference.length() > 100))
                    || (from != null && to != null && !from.isBefore(to))) {
                throw new IllegalArgumentException("invalid transaction search bounds");
            }
        }
    }
}
