package dev.iamkavindu.reconflow.reconciliation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Discrepancy(
        UUID discrepancyId,
        DiscrepancyType type,
        String description,
        String expected,
        String actual,
        Instant detectedAt) {

    public Discrepancy {
        Objects.requireNonNull(discrepancyId, "discrepancyId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(actual, "actual must not be null");
        Objects.requireNonNull(detectedAt, "detectedAt must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }
}
