package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import java.time.Instant;
import java.util.Objects;

public record MatchingWindow(Instant startedAt, Instant expiresAt) {

    public MatchingWindow {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("expiresAt must be after startedAt");
        }
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(expiresAt);
    }
}
