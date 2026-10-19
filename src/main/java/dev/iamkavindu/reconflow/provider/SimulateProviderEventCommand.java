package dev.iamkavindu.reconflow.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public record SimulateProviderEventCommand(String providerEventId, String providerTransactionReference,
        String businessReference, ProviderTransactionType type, BigDecimal amount, Currency currency,
        ProviderTransactionStatus status, Instant occurredAt) {}
