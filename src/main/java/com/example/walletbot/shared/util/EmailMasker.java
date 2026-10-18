package com.example.walletbot.shared.util;

public final class EmailMasker {

    private EmailMasker() {
    }

    public static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "*".repeat(at - 1) + email.substring(at);
    }
}