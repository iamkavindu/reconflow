package dev.iamkavindu.reconflow.provider.internal.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record ProviderMoney(BigDecimal amount, Currency currency) {

    public ProviderMoney {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new IllegalArgumentException("currency must define default fraction digits");
        }
        amount = amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
    }
}
