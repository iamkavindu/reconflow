package dev.iamkavindu.reconflow.transactions;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionView(
        TransactionId transactionId,
        TenantId tenantId,
        String businessReference,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        TransactionStatus status,
        Instant recordedAt) {}
