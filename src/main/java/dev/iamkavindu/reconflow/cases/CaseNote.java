package dev.iamkavindu.reconflow.cases;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CaseNote(UUID noteId, String text, String addedBy, Instant addedAt) {

    public CaseNote {
        Objects.requireNonNull(noteId, "noteId must not be null");
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(addedBy, "addedBy must not be null");
        Objects.requireNonNull(addedAt, "addedAt must not be null");
        text = text.strip();
        if (text.isBlank() || text.length() > 4000 || addedBy.isBlank()) {
            throw new IllegalArgumentException("a note requires text of at most 4000 characters and an actor");
        }
    }
}
