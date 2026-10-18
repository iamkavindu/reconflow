package dev.iamkavindu.reconflow.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public record RecordTransactionCommand(
        String businessReference, TransactionType type, BigDecimal amount, Currency currency, Instant occurredAt) {}
