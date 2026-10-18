package com.example.walletbot.platform.blockchain.util;

import org.ton.java.address.Address;

import java.util.regex.Pattern;

public final class TonAddressFormatter {

    private static final Pattern RAW_FORM = Pattern.compile("^-?\\d+:[0-9a-fA-F]{64}$");

    private TonAddressFormatter() {
    }

    public static String toFriendly(String address) {
        if (address == null || address.isBlank()) return address;
        String trimmed = address.trim();
        if (!RAW_FORM.matcher(trimmed).matches()) return trimmed;
        try {
            return Address.of(trimmed).toNonBounceable();
        } catch (Exception e) {
            return trimmed;
        }
    }
}