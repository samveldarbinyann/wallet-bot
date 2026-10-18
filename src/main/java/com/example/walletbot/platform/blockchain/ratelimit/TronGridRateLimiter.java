package com.example.walletbot.platform.blockchain.ratelimit;

import org.springframework.stereotype.Component;

@Component
public class TronGridRateLimiter {

    private static final long MIN_INTERVAL_MS = 1100;

    private final Object lock = new Object();
    private long lastRequestMs = 0;

    public void acquire() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRequestMs;
            if (elapsed < MIN_INTERVAL_MS) {
                try {
                    Thread.sleep(MIN_INTERVAL_MS - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestMs = System.currentTimeMillis();
        }
    }
}