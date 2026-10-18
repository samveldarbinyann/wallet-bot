package com.example.walletbot.shared.constant;

import java.util.Locale;

public enum SupportedCurrency {
    usd("$"), eur("€"), rub("₽"), amd("֏"), gbp("£");

    public static final SupportedCurrency DEFAULT = usd;

    private final String symbol;

    SupportedCurrency(String symbol) {
        this.symbol = symbol;
    }

    public static SupportedCurrency from(String code) {
        if (code == null) return null;
        String norm = code.trim().toLowerCase(Locale.ROOT);
        for (SupportedCurrency c : values()) {
            if (c.name().equals(norm)) return c;
        }
        return null;
    }

    public String symbol() {
        return symbol;
    }
}
