package dev.iamkavindu.reconflow.cases;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Resolution(
        ResolutionType type,
        String reason,
        String resolvedBy,
        Instant resolvedAt,
        List<String> evidenceReferences) {

    public Resolution {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(resolvedBy, "resolvedBy must not be null");
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        reason = reason.strip();
        evidenceReferences = List.copyOf(evidenceReferences);
        if (reason.isBlank() || reason.length() > 4000 || resolvedBy.isBlank()) {
            throw new IllegalArgumentException("resolution requires a reason of at most 4000 characters and an actor");
        }
    }
}
