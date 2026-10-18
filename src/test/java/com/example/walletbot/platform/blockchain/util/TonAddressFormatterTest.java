package com.example.walletbot.platform.blockchain.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TonAddressFormatterTest {

    @Test
    void toFriendly_rawForm_convertsToFriendly() {
        String raw = "0:" + "0".repeat(64);
        String friendly = TonAddressFormatter.toFriendly(raw);

        assertThat(friendly).isNotEqualTo(raw);
        assertThat(friendly).isNotBlank();
        assertThat(friendly).hasSize(48);
    }

    @Test
    void toFriendly_nonRawForm_returnedTrimmed() {
        assertThat(TonAddressFormatter.toFriendly("  UQsomeFriendlyAddress  ")).isEqualTo("UQsomeFriendlyAddress");
    }

    @Test
    void toFriendly_nullOrBlank_returnedAsIs() {
        assertThat(TonAddressFormatter.toFriendly(null)).isNull();
        assertThat(TonAddressFormatter.toFriendly("   ")).isEqualTo("   ");
    }
}
