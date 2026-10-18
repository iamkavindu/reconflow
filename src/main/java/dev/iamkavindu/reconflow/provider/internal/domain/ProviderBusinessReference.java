package dev.iamkavindu.reconflow.provider.internal.domain;

import java.util.Objects;

public record ProviderBusinessReference(String value) {

    public ProviderBusinessReference {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException("value must be at most 100 characters");
        }
    }
}
