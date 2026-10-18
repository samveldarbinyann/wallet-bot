package com.example.walletbot.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingServiceTest {

    private HashingService hashingService;

    @BeforeEach
    void setUp() {
        hashingService = new HashingService("test-pin-pepper-32-characters-long!!");
    }

    @Test
    void hashOtp_samePepper_sameOutput() {
        String h1 = hashingService.hashOtp("123456");
        String h2 = hashingService.hashOtp("123456");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void hashOtp_differentCodes_differentOutput() {
        String h1 = hashingService.hashOtp("123456");
        String h2 = hashingService.hashOtp("654321");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hashOtp_returnsHexString() {
        String hash = hashingService.hashOtp("000000");
        assertThat(hash).matches("[0-9a-f]+");
    }

    @Test
    void safeEquals_equalStrings_returnsTrue() {
        assertThat(hashingService.safeEquals("hello", "hello")).isTrue();
    }

    @Test
    void safeEquals_differentStrings_returnsFalse() {
        assertThat(hashingService.safeEquals("hello", "world")).isFalse();
    }

    @Test
    void sha256Hex_knownInput_knownOutput() {
        String result = hashingService.sha256Hex("abc");
        assertThat(result).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Hex_sameInput_sameOutput() {
        assertThat(hashingService.sha256Hex("test")).isEqualTo(hashingService.sha256Hex("test"));
    }

    @Test
    void sha256Hex_differentInputs_differentOutputs() {
        assertThat(hashingService.sha256Hex("a")).isNotEqualTo(hashingService.sha256Hex("b"));
    }

    @Test
    void hashEmail_null_returnsNull() {
        assertThat(hashingService.hashEmail(null)).isNull();
    }

    @Test
    void hashEmail_normalizesCaseAndWhitespace() {
        assertThat(hashingService.hashEmail("  Sam@Example.COM  "))
                .isEqualTo(hashingService.hashEmail("sam@example.com"));
    }

    @Test
    void hashEmail_differentEmails_differentOutput() {
        assertThat(hashingService.hashEmail("a@example.com"))
                .isNotEqualTo(hashingService.hashEmail("b@example.com"));
    }

    @Test
    void hashEmail_returnsHexString() {
        assertThat(hashingService.hashEmail("sam@example.com")).matches("[0-9a-f]+");
    }
}
