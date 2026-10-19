package dev.iamkavindu.reconflow.reconciliation.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReconciliationPolicyTest {

    @Test
    void appliesTheConfiguredWindowWithoutChangingTimestampCompatibilityRules() {
        var policy = new ReconciliationPolicy(Duration.ofSeconds(30));
        assertThat(policy.matchingWindowStartingAt(Instant.EPOCH).expiresAt()).isEqualTo(Instant.EPOCH.plusSeconds(30));
        assertThat(policy.maximumTransactionTimeDifference()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void refusesWindowsThatCannotWaitForACounterpart() {
        assertThatThrownBy(() -> new ReconciliationPolicy(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReconciliationPolicy(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
