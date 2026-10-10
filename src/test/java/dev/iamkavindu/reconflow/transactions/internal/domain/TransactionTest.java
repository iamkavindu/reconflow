package dev.iamkavindu.reconflow.transactions.internal.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionStatus;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionTest {
    private static final TransactionId TRANSACTION_ID =
            new TransactionId(UUID.fromString("6e88d343-1db5-4584-a24d-90831a1ddc20"));

    private static final TenantId TENANT_ID = new TenantId(UUID.fromString("f426e733-b132-4a6a-b90b-e279848bd7ef"));

    private static final BusinessReference BUSINESS_REFERENCE = new BusinessReference("ORD-1001");

    private static final Money MONEY = new Money(new BigDecimal("100.00"), Currency.getInstance("USD"));

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-10T09:25:00Z");

    private static final Instant RECORDED_AT = Instant.parse("2026-10-10T09:25:03Z");

    @Test
    void recordsPaymentWithRecordedStatusAndPreservesValues() {
        Transaction transaction = validTransaction();

        assertAll(
                () -> assertEquals(TRANSACTION_ID, transaction.transactionId()),
                () -> assertEquals(TENANT_ID, transaction.tenantId()),
                () -> assertEquals(BUSINESS_REFERENCE, transaction.businessReference()),
                () -> assertEquals(TransactionType.PAYMENT, transaction.type()),
                () -> assertEquals(MONEY, transaction.money()),
                () -> assertEquals(OCCURRED_AT, transaction.occurredAt()),
                () -> assertEquals(TransactionStatus.RECORDED, transaction.status()),
                () -> assertEquals(RECORDED_AT, transaction.recordedAt()));
    }

    @Test
    void rejectsNullRequiredTransactionValues() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> Transaction.record(
                                null,
                                TENANT_ID,
                                BUSINESS_REFERENCE,
                                TransactionType.PAYMENT,
                                MONEY,
                                OCCURRED_AT,
                                RECORDED_AT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> Transaction.record(
                                TRANSACTION_ID,
                                null,
                                BUSINESS_REFERENCE,
                                TransactionType.PAYMENT,
                                MONEY,
                                OCCURRED_AT,
                                RECORDED_AT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> Transaction.record(
                                TRANSACTION_ID,
                                TENANT_ID,
                                null,
                                TransactionType.PAYMENT,
                                MONEY,
                                OCCURRED_AT,
                                RECORDED_AT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> Transaction.record(
                                TRANSACTION_ID, TENANT_ID, BUSINESS_REFERENCE, null, MONEY, OCCURRED_AT, RECORDED_AT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> Transaction.record(
                                TRANSACTION_ID,
                                TENANT_ID,
                                BUSINESS_REFERENCE,
                                TransactionType.PAYMENT,
                                null,
                                OCCURRED_AT,
                                RECORDED_AT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> Transaction.record(
                                TRANSACTION_ID,
                                TENANT_ID,
                                BUSINESS_REFERENCE,
                                TransactionType.PAYMENT,
                                MONEY,
                                null,
                                RECORDED_AT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> Transaction.record(
                                TRANSACTION_ID,
                                TENANT_ID,
                                BUSINESS_REFERENCE,
                                TransactionType.PAYMENT,
                                MONEY,
                                OCCURRED_AT,
                                null)));
    }

    @Test
    void rejectsNullIdentifierValues() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new TransactionId(null)),
                () -> assertThrows(NullPointerException.class, () -> new TenantId(null)));
    }

    @Test
    void rejectsBlankOrOverlongBusinessReference() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new BusinessReference("  \t ")),
                () -> assertThrows(IllegalArgumentException.class, () -> new BusinessReference("x".repeat(256))));
    }

    @Test
    void rejectsInvalidMoney() {
        Currency usd = Currency.getInstance("USD");

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new Money(null, usd)),
                () -> assertThrows(NullPointerException.class, () -> new Money(BigDecimal.ONE, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.ZERO, usd)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("-1.00"), usd)));
    }

    private static Transaction validTransaction() {
        return Transaction.record(
                TRANSACTION_ID,
                TENANT_ID,
                BUSINESS_REFERENCE,
                TransactionType.PAYMENT,
                MONEY,
                OCCURRED_AT,
                RECORDED_AT);
    }
}
