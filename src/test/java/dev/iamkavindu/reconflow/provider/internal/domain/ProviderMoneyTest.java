package dev.iamkavindu.reconflow.provider.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class ProviderMoneyTest {

    @Test
    void normalizesAmountToCurrencyFractionDigits() {
        ProviderMoney money = new ProviderMoney(new BigDecimal("100"), Currency.getInstance("USD"));

        assertThat(money.amount()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> new ProviderMoney(BigDecimal.ZERO, Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void rejectsExcessFractionalPrecision() {
        assertThatThrownBy(() -> new ProviderMoney(new BigDecimal("10.001"), Currency.getInstance("USD")))
                .isInstanceOf(ArithmeticException.class);
    }
}
