package com.example.walletbot.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMaskerTest {

    @Test
    void mask_normalEmail_masksLocalPart() {
        assertThat(EmailMasker.mask("sam@example.com")).isEqualTo("s**@example.com");
    }

    @Test
    void mask_twoCharLocal_masksSingleChar() {
        assertThat(EmailMasker.mask("ab@x.com")).isEqualTo("a*@x.com");
    }

    @Test
    void mask_singleCharLocal_returnedAsIs() {
        assertThat(EmailMasker.mask("a@example.com")).isEqualTo("a@example.com");
    }

    @Test
    void mask_longerLocal_masksAllButFirst() {
        assertThat(EmailMasker.mask("hello@domain.org")).isEqualTo("h****@domain.org");
    }

    @Test
    void mask_preservesDomainExactly() {
        String result = EmailMasker.mask("user@sub.domain.co.uk");
        assertThat(result).endsWith("@sub.domain.co.uk");
    }
}
