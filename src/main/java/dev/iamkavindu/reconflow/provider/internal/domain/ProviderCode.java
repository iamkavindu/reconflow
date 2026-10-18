package dev.iamkavindu.reconflow.provider.internal.domain;

import java.util.Locale;
import java.util.Objects;

public record ProviderCode(String value) {

    public ProviderCode {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > 50) {
            throw new IllegalArgumentException("value must be at most 50 characters");
        }
    }
}
