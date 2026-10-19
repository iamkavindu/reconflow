package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.reconciliation.internal.domain.MatchingWindow;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
class ReconciliationPolicy {

    private final Duration matchingWindow;
    private static final Duration MAXIMUM_TRANSACTION_TIME_DIFFERENCE = Duration.ofMinutes(10);

    ReconciliationPolicy(@Value("${reconflow.reconciliation.matching-window:PT10M}") Duration matchingWindow) {
        if (matchingWindow == null || matchingWindow.isZero() || matchingWindow.isNegative()) {
            throw new IllegalArgumentException("the matching window must be positive");
        }
        this.matchingWindow = matchingWindow;
    }

    Duration maximumTransactionTimeDifference() {
        return MAXIMUM_TRANSACTION_TIME_DIFFERENCE;
    }

    MatchingWindow matchingWindowStartingAt(Instant startedAt) {
        return new MatchingWindow(startedAt, startedAt.plus(matchingWindow));
    }
}
