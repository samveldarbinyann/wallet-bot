package com.example.walletbot.wallet.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QrShareTokenStoreTest {

    private QrShareTokenStore store;

    @BeforeEach
    void setUp() {
        store = new QrShareTokenStore();
    }

    @Test
    void store_thenResolve_returnsAddress() {
        String token = store.store("0xABC");

        assertThat(token).isNotBlank();
        assertThat(store.resolve(token)).isEqualTo("0xABC");
    }

    @Test
    void resolve_unknownToken_null() {
        assertThat(store.resolve("deadbeef")).isNull();
    }

    @Test
    void store_producesDistinctTokensForSameAddress() {
        String t1 = store.store("0xABC");
        String t2 = store.store("0xABC");

        assertThat(t1).isNotEqualTo(t2);
        assertThat(store.resolve(t1)).isEqualTo("0xABC");
        assertThat(store.resolve(t2)).isEqualTo("0xABC");
    }

    @Test
    void store_tokenIsHexEncoded() {
        String token = store.store("0xABC");
        assertThat(token).matches("[0-9a-f]+");
    }
}
