package com.example.walletbot.shared.constant;

public enum SupportedLanguage {
    en, ru;

    public static final SupportedLanguage DEFAULT = en;

    public static SupportedLanguage fromTag(String tag) {
        if (tag == null) return null;
        String norm = tag.trim().toLowerCase();
        for (SupportedLanguage lang : values()) {
            if (norm.startsWith(lang.name())) return lang;
        }
        return null;
    }
}
