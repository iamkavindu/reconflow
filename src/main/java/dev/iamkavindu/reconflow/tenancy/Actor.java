package dev.iamkavindu.reconflow.tenancy;

import java.util.Objects;

public record Actor(TenantId tenantId, String subject, UserRole role) {

    public Actor {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("actor subject must not be blank");
        }
    }

    public void requireAnalyst() {
        if (role != UserRole.ADMIN && role != UserRole.ANALYST) {
            throw new OperationNotAuthorizedException("an analyst or administrator role is required");
        }
    }
}
