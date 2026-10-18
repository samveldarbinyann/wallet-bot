package com.example.walletbot.platform.blockchain.cache;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class TronAccountInFlightCache {

    private final Map<String, CompletableFuture<Map<String, Object>>> inFlight = new ConcurrentHashMap<>();

    public Map<String, Object> dedup(String address, Supplier<Map<String, Object>> supplier) {
        return inFlight.computeIfAbsent(address, addr -> CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } finally {
                inFlight.remove(addr);
            }
        })).join();
    }
}