package com.example.walletbot.platform.blockchain.ratelimit;

public class TonCenterRateLimiter {

    private final long minIntervalMs;
    private final Object lock = new Object();
    private long lastRequestMs = 0;

    public TonCenterRateLimiter(int requestsPerSecond) {
        if (requestsPerSecond <= 0) {
            this.minIntervalMs = 0L;
        } else {
            this.minIntervalMs = 1000L / requestsPerSecond;
        }
    }

    public void acquire() {
        if (minIntervalMs == 0L) return;
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRequestMs;
            if (elapsed < minIntervalMs) {
                try {
                    Thread.sleep(minIntervalMs - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestMs = System.currentTimeMillis();
        }
    }
}