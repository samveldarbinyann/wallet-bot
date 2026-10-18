package com.example.walletbot.platform.blockchain.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class JettonWalletAddressCache {

    private final Cache<String, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    public String resolve(String ownerAddress, Function<String, String> resolver) {
        String cached = cache.getIfPresent(ownerAddress);
        if (cached != null) return cached;
        String resolved = resolver.apply(ownerAddress);
        if (resolved != null) {
            cache.put(ownerAddress, resolved);
        }
        return resolved;
    }

    public void invalidate(String ownerAddress) {
        cache.invalidate(ownerAddress);
    }
}