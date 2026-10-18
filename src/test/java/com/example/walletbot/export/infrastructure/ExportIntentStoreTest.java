package com.example.walletbot.export.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExportIntentStoreTest {

    private ExportIntentStore store;

    @BeforeEach
    void setUp() {
        store = new ExportIntentStore();
    }

    @Test
    void issue_thenPeek_isIssued() {
        store.issue("jti-1");
        assertThat(store.peek("jti-1")).isEqualTo(ExportIntentStore.State.ISSUED);
    }

    @Test
    void peek_unknown_null() {
        assertThat(store.peek("nope")).isNull();
    }

    @Test
    void consume_issued_returnsIssuedAndTransitionsToConsumed() {
        store.issue("jti-1");

        assertThat(store.consume("jti-1")).isEqualTo(ExportIntentStore.State.ISSUED);
        assertThat(store.peek("jti-1")).isEqualTo(ExportIntentStore.State.CONSUMED);
    }

    @Test
    void consume_secondTime_returnsConsumedWithoutReissuing() {
        store.issue("jti-1");
        store.consume("jti-1");

        assertThat(store.consume("jti-1")).isEqualTo(ExportIntentStore.State.CONSUMED);
    }

    @Test
    void consume_unknown_returnsNull() {
        assertThat(store.consume("nope")).isNull();
    }

    @Test
    void consume_concurrent_onlyOneThreadGetsIssued() throws InterruptedException {
        store.issue("jti-1");

        int threads = 20;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger issuedCount = new AtomicInteger();
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                latch.countDown();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (store.consume("jti-1") == ExportIntentStore.State.ISSUED) issuedCount.incrementAndGet();
            });
        }
        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(issuedCount.get()).isEqualTo(1);
    }
}
