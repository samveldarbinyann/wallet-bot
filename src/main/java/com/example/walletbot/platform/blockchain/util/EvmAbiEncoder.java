package com.example.walletbot.platform.blockchain.util;

import com.example.walletbot.shared.util.HexUtils;

import java.math.BigInteger;

public final class EvmAbiEncoder {

    public static final String BALANCE_OF_SELECTOR = "0x70a08231";
    public static final String TRANSFER_SELECTOR = "0xa9059cbb";

    private EvmAbiEncoder() {
    }

    public static String encodeBalanceOf(String ownerAddress) {
        return BALANCE_OF_SELECTOR + leftPad64(HexUtils.stripHexPrefix(ownerAddress));
    }

    public static String encodeTransfer(String recipient, BigInteger amount) {
        return TRANSFER_SELECTOR
                + leftPad64(HexUtils.stripHexPrefix(recipient))
                + leftPad64(amount.toString(16));
    }

    public static String leftPad64(String hex) {
        if (hex.length() >= 64) return hex;
        return "0".repeat(64 - hex.length()) + hex;
    }
}