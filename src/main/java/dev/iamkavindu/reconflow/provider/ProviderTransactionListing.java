package dev.iamkavindu.reconflow.provider;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProviderTransactionListing {
    List<ProviderTransactionView> search(TenantId tenantId, Filter filter);

    record Filter(UUID providerTransactionId, String providerReference, String reference, Instant from, Instant to, int limit, int offset) {
        public Filter {
            if (limit < 1 || limit > 100 || offset < 0 || offset > 10000
                    || (reference != null && (reference.isBlank() || reference.length() > 100))
                    || (providerReference != null && (providerReference.isBlank() || providerReference.length() > 200))
                    || (from != null && to != null && !from.isBefore(to))) {
                throw new IllegalArgumentException("invalid provider search bounds");
            }
        }
    }
}
