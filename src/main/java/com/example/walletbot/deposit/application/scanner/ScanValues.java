package com.example.walletbot.deposit.application.scanner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

final class ScanValues {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final long MS_THRESHOLD = 10_000_000_000L;

    private ScanValues() {
    }

    static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static BigDecimal nanoToTon(Object v) {
        try {
            return new BigDecimal(String.valueOf(v))
                    .divide(new BigDecimal("1000000000"), 9, RoundingMode.HALF_UP).stripTrailingZeros();
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    static LocalDateTime unixSecondsToUtc(Object value) {
        if (value == null)
            return null;
        try {
            long v = Long.parseLong(String.valueOf(value));
            return LocalDateTime.ofInstant(
                    v > MS_THRESHOLD ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v), UTC);
        } catch (Exception e) {
            return null;
        }
    }

    static LocalDateTime unixMillisToUtc(Object value) {
        if (value == null)
            return null;
        try {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(String.valueOf(value))), UTC);
        } catch (Exception e) {
            return null;
        }
    }
}
