package com.example.walletbot.platform.web.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkNameValidatorTest {

    private NetworkNameValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NetworkNameValidator();
    }

    @Test
    void isValid_knownNetworkCaseInsensitiveTrimmed() {
        assertThat(validator.isValid("  eth ", null)).isTrue();
        assertThat(validator.isValid("BTC", null)).isTrue();
    }

    @Test
    void isValid_null_false() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    void isValid_blank_false() {
        assertThat(validator.isValid("   ", null)).isFalse();
    }

    @Test
    void isValid_unknown_false() {
        assertThat(validator.isValid("NOTACHAIN", null)).isFalse();
    }
}
