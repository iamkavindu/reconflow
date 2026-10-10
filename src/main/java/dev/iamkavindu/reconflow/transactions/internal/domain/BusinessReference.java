package dev.iamkavindu.reconflow.transactions.internal.domain;

import java.util.Objects;

public record BusinessReference(String value) {
    public BusinessReference {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.strip();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException("value must be at most 100 characters");
        }
    }
}
