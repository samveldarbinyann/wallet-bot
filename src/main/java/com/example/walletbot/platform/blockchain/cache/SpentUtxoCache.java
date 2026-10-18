package com.example.walletbot.platform.blockchain.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SpentUtxoCache {

    private final Cache<String, Boolean> spent = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .maximumSize(100_000)
            .build();

    private static String key(String txHash, long index) {
        return txHash + ":" + index;
    }

    public boolean isAvailable(String txHash, long index) {
        return spent.getIfPresent(key(txHash, index)) == null;
    }

    public void markAsSpent(String txHash, long index) {
        spent.put(key(txHash, index), Boolean.TRUE);
    }
}