package com.example.walletbot.export.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExportSessionStoreTest {

    private ExportSessionStore store;

    @BeforeEach
    void setUp() {
        store = new ExportSessionStore();
    }

    @Test
    void tryReveal_validSession_ok_andDoesNotBurn() {
        store.open("sid", 1L, 5);

        ExportSessionStore.RevealOutcome first = store.tryReveal("sid", 1L, 5);
        assertThat(first.result()).isEqualTo(ExportSessionStore.RevealResult.OK);

        ExportSessionStore.RevealOutcome second = store.tryReveal("sid", 1L, 5);
        assertThat(second.result()).isEqualTo(ExportSessionStore.RevealResult.OK);
    }

    @Test
    void tryReveal_unknownSession_expired() {
        assertThat(store.tryReveal("nope", 1L, 5).result())
                .isEqualTo(ExportSessionStore.RevealResult.EXPIRED);
    }

    @Test
    void tryReveal_wrongUser_burnsSession() {
        store.open("sid", 1L, 5);

        assertThat(store.tryReveal("sid", 2L, 5).result())
                .isEqualTo(ExportSessionStore.RevealResult.WRONG_USER);
        assertThat(store.tryReveal("sid", 1L, 5).result())
                .isEqualTo(ExportSessionStore.RevealResult.EXPIRED);
    }

    @Test
    void tryReveal_staleTokenVersion_expiredAndBurned() {
        store.open("sid", 1L, 5);

        assertThat(store.tryReveal("sid", 1L, 4).result())
                .isEqualTo(ExportSessionStore.RevealResult.EXPIRED);
        assertThat(store.tryReveal("sid", 1L, 5).result())
                .isEqualTo(ExportSessionStore.RevealResult.EXPIRED);
    }

    @Test
    void burn_makesSubsequentRevealExpired() {
        store.open("sid", 1L, 5);
        store.burn("sid");

        assertThat(store.tryReveal("sid", 1L, 5).result())
                .isEqualTo(ExportSessionStore.RevealResult.EXPIRED);
    }

    @Test
    void tryReveal_concurrent_allSucceedWithinTtl() throws InterruptedException {
        store.open("sid", 1L, 5);

        int threads = 30;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                latch.countDown();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (store.tryReveal("sid", 1L, 5).result() == ExportSessionStore.RevealResult.OK) {
                    successCount.incrementAndGet();
                }
            });
        }
        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(threads);
    }
}
