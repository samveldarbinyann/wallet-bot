package com.example.walletbot.platform.web.security;

import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.shared.ratelimit.KeyedBucketLimiter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AdminMutationRateLimiter extends KeyedBucketLimiter<Long> {

    public AdminMutationRateLimiter() {
        super(Duration.ofMinutes(5), 1000);
    }

    @Override
    protected Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(30).refillGreedy(30, Duration.ofMinutes(1)).build())
                .build();
    }

    public boolean isAllowed(Long userId) {
        return tryConsume(userId);
    }

    public void assertAllowed(Long adminId) {
        if (!isAllowed(adminId)) {
            throw new RateLimitException("Admin mutation rate limit exceeded");
        }
    }
}
