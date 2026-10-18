package dev.iamkavindu.reconflow.cases;

import java.util.Objects;
import java.util.UUID;

public record CaseId(UUID value) {

    public CaseId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
