package com.example.walletbot.shared.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;

public abstract class WalletActionLimiter {

    private final int maxPerWindow;
    private final long windowSeconds;
    private final long walletCooldownSeconds;

    private final Cache<Long, ConcurrentLinkedDeque<Instant>> userTimes;
    private final Cache<Long, Instant> walletLast;

    protected WalletActionLimiter(int maxPerWindow, long windowSeconds, long walletCooldownSeconds) {
        this.maxPerWindow = maxPerWindow;
        this.windowSeconds = windowSeconds;
        this.walletCooldownSeconds = walletCooldownSeconds;
        Duration idleTtl = Duration.ofSeconds(Math.max(windowSeconds, walletCooldownSeconds) * 2);
        this.userTimes = Caffeine.newBuilder().expireAfterAccess(idleTtl).maximumSize(50_000).build();
        this.walletLast = Caffeine.newBuilder().expireAfterAccess(idleTtl).maximumSize(50_000).build();
    }

    public boolean isAllowed(long userId, long walletId) {
        Instant now = Instant.now();

        Instant lastWalletAction = walletLast.getIfPresent(walletId);
        if (lastWalletAction != null && now.isBefore(lastWalletAction.plusSeconds(walletCooldownSeconds))) {
            return false;
        }

        ConcurrentLinkedDeque<Instant> times = userTimes.get(userId, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.pollFirst();
        }
        return times.size() < maxPerWindow;
    }

    public void record(long userId, long walletId) {
        Instant now = Instant.now();
        userTimes.get(userId, k -> new ConcurrentLinkedDeque<>()).addLast(now);
        walletLast.put(walletId, now);
    }
}
