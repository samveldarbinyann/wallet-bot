package com.example.walletbot.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableAsync;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    void asyncMustBeEnabled() {
        assertThat(AsyncConfig.class.isAnnotationPresent(EnableAsync.class))
                .as("@EnableAsync must stay on AsyncConfig — the @Async notification dispatchers rely on it")
                .isTrue();
    }
}
