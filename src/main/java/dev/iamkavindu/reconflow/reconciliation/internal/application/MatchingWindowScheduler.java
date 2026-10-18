package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationMaintenance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "reconflow.reconciliation", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
class MatchingWindowScheduler {

    private final ReconciliationMaintenance maintenance;

    MatchingWindowScheduler(ReconciliationMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Scheduled(fixedDelayString = "${reconflow.reconciliation.expiry-scan-delay:5000}")
    void expireMatchingWindows() {
        maintenance.processExpiredMatchingWindows();
    }
}
