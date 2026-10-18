package dev.iamkavindu.reconflow.provider.internal.domain;

import java.util.Objects;

public record ProviderEventId(String value) {

    public ProviderEventId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > 200) {
            throw new IllegalArgumentException("value must be at most 200 characters");
        }
    }
}
