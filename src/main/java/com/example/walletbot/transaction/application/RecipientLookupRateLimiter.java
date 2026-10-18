package com.example.walletbot.transaction.application;

import com.example.walletbot.shared.ratelimit.KeyedBucketLimiter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RecipientLookupRateLimiter extends KeyedBucketLimiter<Long> {

    public RecipientLookupRateLimiter() {
        super(Duration.ofMinutes(10), 50_000);
    }

    @Override
    protected Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(30).refillGreedy(30, Duration.ofMinutes(1)).build())
                .build();
    }

    private final Cache<String, Boolean> recentlyResolved = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(50_000)
            .build();

    public boolean isAllowed(Long userId) {
        return tryConsume(userId);
    }

    public boolean isAllowedForEstimate(Long userId, String username) {
        if (recentlyResolved.getIfPresent(cacheKey(userId, username)) != null) {
            return true;
        }
        return tryConsume(userId);
    }

    public void markResolved(Long userId, String username) {
        recentlyResolved.put(cacheKey(userId, username), Boolean.TRUE);
    }

    private static String cacheKey(Long userId, String username) {
        String normalized = RecipientResolver.normalize(username);
        return userId + ":" + (normalized != null ? normalized : username).toLowerCase();
    }
}