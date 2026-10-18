package com.example.walletbot.platform.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretMaskerTest {

    @Test
    void masksHeliusQueryKey() {
        assertThat(SecretMasker.mask("POST https://mainnet.helius-rpc.com/?api-key=abc123XYZ"))
                .isEqualTo("POST https://mainnet.helius-rpc.com/?api-key=***");
    }

    @Test
    void masksTonCenterQueryKey() {
        assertThat(SecretMasker.mask("https://toncenter.com/api/v2/getTx?address=EQ..&api_key=ton_SECRET_99"))
                .isEqualTo("https://toncenter.com/api/v2/getTx?address=EQ..&api_key=***");
    }

    @Test
    void masksAlchemyPathKey() {
        assertThat(SecretMasker.mask("connect to https://eth-mainnet.g.alchemy.com/v2/MyAlchemyKey failed"))
                .isEqualTo("connect to https://eth-mainnet.g.alchemy.com/v2/*** failed");
    }

    @Test
    void preservesFailureReasonAroundKey() {
        String in = "I/O error on POST request for \"https://mainnet.helius-rpc.com/?api-key=SECRET\": "
                + "Connection refused";
        String out = SecretMasker.mask(in);
        assertThat(out).contains("api-key=***");
        assertThat(out).doesNotContain("SECRET");
        assertThat(out).contains("I/O error on POST request");
        assertThat(out).contains("Connection refused");
    }

    @Test
    void leavesLinesWithoutSecretsUntouched() {
        String line = "Token balance retrieval error on ETH for 0xabc: Connection timed out";
        assertThat(SecretMasker.mask(line)).isEqualTo(line);
    }

    @Test
    void doesNotMaskUnrelatedParamThatMerelyEndsInKey() {
        String line = "redirect to https://example.com/?monkey=banana";
        assertThat(SecretMasker.mask(line)).isEqualTo(line);
    }

    @Test
    void masksMultipleKeysOnOneLine() {
        String out = SecretMasker.mask(
                "a=https://x/?api-key=ONE b=https://eth-mainnet.g.alchemy.com/v2/TWO");
        assertThat(out).doesNotContain("ONE").doesNotContain("TWO");
        assertThat(out).contains("api-key=***").contains("/v2/***");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(SecretMasker.mask(null)).isNull();
        assertThat(SecretMasker.mask("")).isEmpty();
    }
}
