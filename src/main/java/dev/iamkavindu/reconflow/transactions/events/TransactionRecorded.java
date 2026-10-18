package dev.iamkavindu.reconflow.transactions.events;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TransactionRecorded(
        UUID eventId,
        TenantId tenantId,
        TransactionId transactionId,
        String businessReference,
        Instant recordedAt,
        UUID correlationId,
        String recordedBy) {

    public TransactionRecorded(
            UUID eventId,
            TenantId tenantId,
            TransactionId transactionId,
            String businessReference,
            Instant recordedAt,
            UUID correlationId) {
        this(eventId, tenantId, transactionId, businessReference, recordedAt, correlationId, null);
    }

    public TransactionRecorded {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(businessReference, "businessReference must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }
}
