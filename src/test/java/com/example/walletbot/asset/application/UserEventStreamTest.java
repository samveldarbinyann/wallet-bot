package com.example.walletbot.asset.application;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UserEventStreamTest {

    private final UserEventStream stream = new UserEventStream();

    @Test
    void register_returnsEmitter() {
        SseEmitter emitter = stream.register(1L);

        assertThat(emitter).isNotNull();
    }

    @Test
    void pushBalanceChanged_unknownUserOrNull_isNoOp() {
        assertThatCode(() -> {
            stream.pushBalanceChanged(999L);
            stream.pushBalanceChanged(null);
        }).doesNotThrowAnyException();
    }

    @Test
    void pushBalanceChanged_toRegisteredUser_doesNotThrow() {
        stream.register(7L);

        assertThatCode(() -> stream.pushBalanceChanged(7L)).doesNotThrowAnyException();
    }
}
