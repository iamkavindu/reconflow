package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MatchingWindowTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-10T09:25:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-10T09:35:00Z");

    @Test
    void requiresExpiryAfterStart() {
        assertThatThrownBy(() -> new MatchingWindow(STARTED_AT, STARTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after startedAt");
    }

    @Test
    void isExpiredAtOrAfterExpiry() {
        MatchingWindow window = new MatchingWindow(STARTED_AT, EXPIRES_AT);

        assertThat(window.isExpired(EXPIRES_AT.minusNanos(1))).isFalse();
        assertThat(window.isExpired(EXPIRES_AT)).isTrue();
        assertThat(window.isExpired(EXPIRES_AT.plusSeconds(1))).isTrue();
    }
}
