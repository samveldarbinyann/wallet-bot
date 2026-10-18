package com.example.walletbot.shared.security;

import com.example.walletbot.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingTokenStoreTest {

    private PendingTokenStore store;

    @BeforeEach
    void setUp() {
        store = new PendingTokenStore();
    }

    @Test
    void consumeOrThrow_firstCall_succeeds() {
        assertThatCode(() -> store.consumeOrThrow("jti-1")).doesNotThrowAnyException();
    }

    @Test
    void consumeOrThrow_secondCallSameJti_throwsReplay() {
        store.consumeOrThrow("jti-1");

        assertThatThrownBy(() -> store.consumeOrThrow("jti-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.pending_token_used");
    }

    @Test
    void consumeOrThrow_distinctJtis_bothSucceed() {
        store.consumeOrThrow("jti-1");
        assertThatCode(() -> store.consumeOrThrow("jti-2")).doesNotThrowAnyException();
    }

    @Test
    void consumeOrThrow_nullJti_failsClosed() {
        assertThatThrownBy(() -> store.consumeOrThrow(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.pending_token_used");
    }

    @Test
    void consumeOrThrow_blankJti_failsClosed() {
        assertThatThrownBy(() -> store.consumeOrThrow("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.pending_token_used");
    }
}