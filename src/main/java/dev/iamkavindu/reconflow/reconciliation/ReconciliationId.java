package dev.iamkavindu.reconflow.reconciliation;

import java.util.Objects;
import java.util.UUID;

public record ReconciliationId(UUID value) {

    public ReconciliationId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
