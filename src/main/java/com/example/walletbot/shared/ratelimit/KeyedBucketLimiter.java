package com.example.walletbot.shared.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;

import java.time.Duration;

public abstract class KeyedBucketLimiter<K> {

    private final Cache<K, Bucket> buckets;

    protected KeyedBucketLimiter(Duration idleTtl, long maxKeys) {
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(idleTtl)
                .maximumSize(maxKeys)
                .build();
    }

    protected abstract Bucket newBucket();

    protected boolean tryConsume(K key) {
        return buckets.get(key, k -> newBucket()).tryConsume(1);
    }
}
