package dev.iamkavindu.reconflow.transactions;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) {
    public TransactionId {
        Objects.requireNonNull(value, "value cannot be null");
    }
}
