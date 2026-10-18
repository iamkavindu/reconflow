package dev.iamkavindu.reconflow.provider;

import java.util.Objects;
import java.util.UUID;

public record ProviderTransactionId(UUID value) {

    public ProviderTransactionId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
