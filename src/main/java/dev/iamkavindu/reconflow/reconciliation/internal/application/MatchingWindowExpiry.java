package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationMaintenance;
import dev.iamkavindu.reconflow.reconciliation.internal.domain.ReconciliationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class MatchingWindowExpiry implements ReconciliationMaintenance {

    private final ReconciliationRepository repository;
    private final AutomaticReconciliation reconciliation;
    private final Clock clock;

    MatchingWindowExpiry(ReconciliationRepository repository, AutomaticReconciliation reconciliation, Clock clock) {
        this.repository = repository;
        this.reconciliation = reconciliation;
        this.clock = clock;
    }

    @Override
    public void processExpiredMatchingWindows() {
        for (var pending : repository.findPendingExpiredBefore(Instant.now(clock), 100)) {
            reconciliation.attempt(
                    pending.tenantId(), pending.businessReference(), UUID.randomUUID(), UUID.randomUUID());
        }
    }
}
