package com.example.walletbot.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AmountFormatter {

    private static final int MIN_FRACTION_DIGITS = 2;

    private AmountFormatter() {
    }

    public static String forDisplay(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        int scale = Math.max(stripped.scale(), MIN_FRACTION_DIGITS);
        return stripped.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }
}