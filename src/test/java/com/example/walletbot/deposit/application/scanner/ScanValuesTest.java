package com.example.walletbot.deposit.application.scanner;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScanValuesTest {

    @Test
    void asString_returnsNullForNull() {
        assertThat(ScanValues.asString(null)).isNull();
    }

    @Test
    void asString_stringifiesNonNull() {
        assertThat(ScanValues.asString(123)).isEqualTo("123");
        assertThat(ScanValues.asString("abc")).isEqualTo("abc");
    }

    @Test
    void nanoToTon_convertsNanoToWholeUnits() {
        assertThat(ScanValues.nanoToTon("1000000000")).isEqualByComparingTo("1");
        assertThat(ScanValues.nanoToTon(2_500_000_000L)).isEqualByComparingTo("2.5");
    }

    @Test
    void nanoToTon_zeroStaysZero() {
        assertThat(ScanValues.nanoToTon("0")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nanoToTon_returnsZeroForJunk() {
        assertThat(ScanValues.nanoToTon("not-a-number")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ScanValues.nanoToTon(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unixSecondsToUtc_parsesEpochSeconds() {
        assertThat(ScanValues.unixSecondsToUtc(1_700_000_000L))
                .isEqualTo(LocalDateTime.of(2023, 11, 14, 22, 13, 20));
    }

    @Test
    void unixSecondsToUtc_treatsLargeValuesAsMillis() {
        assertThat(ScanValues.unixSecondsToUtc(1_700_000_000_000L))
                .isEqualTo(ScanValues.unixSecondsToUtc(1_700_000_000L));
    }

    @Test
    void unixSecondsToUtc_returnsNullForNullOrJunk() {
        assertThat(ScanValues.unixSecondsToUtc(null)).isNull();
        assertThat(ScanValues.unixSecondsToUtc("garbage")).isNull();
    }

    @Test
    void unixMillisToUtc_parsesEpochMillis() {
        assertThat(ScanValues.unixMillisToUtc(1_700_000_000_000L))
                .isEqualTo(LocalDateTime.of(2023, 11, 14, 22, 13, 20));
    }

    @Test
    void unixMillisToUtc_returnsNullForNullOrJunk() {
        assertThat(ScanValues.unixMillisToUtc(null)).isNull();
        assertThat(ScanValues.unixMillisToUtc("garbage")).isNull();
    }
}