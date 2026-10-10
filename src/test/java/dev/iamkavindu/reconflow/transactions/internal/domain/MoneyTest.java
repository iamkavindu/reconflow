package dev.iamkavindu.reconflow.transactions.internal.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void normalizesAmountToCurrencyScale() {
        Money money = new Money(new BigDecimal("100.0"), USD);

        assertEquals(new BigDecimal("100.00"), money.amount());
    }

    @Test
    void amountsWithDifferentInputScalesAreEqual() {
        Money oneDecimalPlace = new Money(new BigDecimal("100.0"), USD);
        Money twoDecimalPlaces = new Money(new BigDecimal("100.00"), USD);

        assertEquals(oneDecimalPlace, twoDecimalPlaces);
        assertEquals(oneDecimalPlace.hashCode(), twoDecimalPlaces.hashCode());
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class, () -> new Money(null, USD));
    }

    @Test
    void rejectsNullCurrency() {
        assertThrows(NullPointerException.class, () -> new Money(BigDecimal.ONE, null));
    }

    @Test
    void rejectsZeroAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.ZERO, USD));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("-1.00"), USD));
    }

    @Test
    void rejectsAmountThatRequiresRounding() {
        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("100.001"), USD));
    }

    @Test
    void acceptsExtraTrailingZerosWhenValueIsExact() {
        Money money = new Money(new BigDecimal("100.000"), USD);

        assertEquals(new BigDecimal("100.00"), money.amount());
    }

    @Test
    void normalizesCurrenciesWithZeroFractionDigits() {
        Currency jpy = Currency.getInstance("JPY");

        Money money = new Money(new BigDecimal("100.0"), jpy);

        assertEquals(new BigDecimal("100"), money.amount());
    }

    @Test
    void rejectsFractionalAmountForCurrencyWithZeroFractionDigits() {
        Currency jpy = Currency.getInstance("JPY");

        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("100.1"), jpy));
    }

    @Test
    void rejectsCurrencyWithoutDefinedFractionDigits() {
        Currency noCurrency = Currency.getInstance("XXX");

        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.ONE, noCurrency));
    }
}
