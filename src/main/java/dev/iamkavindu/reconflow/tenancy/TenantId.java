package dev.iamkavindu.reconflow.tenancy;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {
    public TenantId {
        Objects.requireNonNull(value, "value cannot be null");
    }
}
