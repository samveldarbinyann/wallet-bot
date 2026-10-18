package com.example.walletbot.shared.constant;

import java.util.Locale;

public enum UiTheme {
    telegram, dark, light;

    public static final UiTheme DEFAULT = dark;

    public static UiTheme from(String code) {
        if (code == null) return null;
        String norm = code.trim().toLowerCase(Locale.ROOT);
        for (UiTheme t : values()) {
            if (t.name().equals(norm)) return t;
        }
        return null;
    }
}
