package dev.iamkavindu.reconflow.provider;

import java.math.BigDecimal;
import java.util.Objects;

public record SettlementLine(
        String providerTransactionReference, BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount) {

    public SettlementLine {
        Objects.requireNonNull(providerTransactionReference);
        Objects.requireNonNull(grossAmount);
        Objects.requireNonNull(feeAmount);
        Objects.requireNonNull(netAmount);
        providerTransactionReference = providerTransactionReference.strip();
        if (providerTransactionReference.isBlank()
                || providerTransactionReference.length() > 200
                || feeAmount.signum() < 0
                || grossAmount.subtract(feeAmount).compareTo(netAmount) != 0) {
            throw new IllegalArgumentException(
                    "a settlement line requires a reference and gross minus fees equal to net");
        }
    }
}
