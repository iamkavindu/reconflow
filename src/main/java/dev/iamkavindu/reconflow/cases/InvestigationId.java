package dev.iamkavindu.reconflow.cases;

import java.util.Objects;
import java.util.UUID;

public record InvestigationId(UUID value) {

    public InvestigationId {
        Objects.requireNonNull(value);
    }
}
