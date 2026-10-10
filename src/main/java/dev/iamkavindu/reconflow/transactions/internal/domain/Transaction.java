package dev.iamkavindu.reconflow.transactions.internal.domain;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionStatus;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import java.time.Instant;
import java.util.Objects;

public class Transaction {
    private TransactionId transactionId;
    private TenantId tenantId;
    private BusinessReference businessReference;
    private TransactionType type;
    private Money money;
    private Instant occurredAt;
    private TransactionStatus status;
    private Instant recordedAt;

    private Transaction(
            TransactionId transactionId,
            TenantId tenantId,
            BusinessReference businessReference,
            TransactionType type,
            Money money,
            Instant occurredAt,
            Instant recordedAt) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.businessReference = Objects.requireNonNull(businessReference, "businessReference must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.money = Objects.requireNonNull(money, "money must not be null");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        this.status = TransactionStatus.RECORDED;
    }

    public static Transaction record(
            TransactionId transactionId,
            TenantId tenantId,
            BusinessReference businessReference,
            TransactionType type,
            Money money,
            Instant occurredAt,
            Instant recordedAt) {
        return new Transaction(transactionId, tenantId, businessReference, type, money, occurredAt, recordedAt);
    }

    public TransactionId transactionId() {
        return transactionId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public BusinessReference businessReference() {
        return businessReference;
    }

    public TransactionType type() {
        return type;
    }

    public Money money() {
        return money;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public TransactionStatus status() {
        return status;
    }

    public Instant recordedAt() {
        return recordedAt;
    }
}
