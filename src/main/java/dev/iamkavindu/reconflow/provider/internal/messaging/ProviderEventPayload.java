package dev.iamkavindu.reconflow.provider.internal.messaging;

import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import java.math.BigDecimal;
import java.time.Instant;

record ProviderEventPayload(
        String providerEventId,
        String provider,
        String providerTransactionReference,
        String businessReference,
        ProviderTransactionType type,
        BigDecimal amount,
        String currency,
        ProviderTransactionStatus status,
        Instant occurredAt) {}
