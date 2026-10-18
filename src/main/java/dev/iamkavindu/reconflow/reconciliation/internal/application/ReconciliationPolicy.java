package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.reconciliation.internal.domain.MatchingWindow;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class ReconciliationPolicy {

    private static final Duration MATCHING_WINDOW = Duration.ofMinutes(10);

    MatchingWindow matchingWindowStartingAt(Instant startedAt) {
        return new MatchingWindow(startedAt, startedAt.plus(MATCHING_WINDOW));
    }
}
