package com.example.walletbot.platform.web.security;

import com.example.walletbot.shared.ratelimit.KeyedBucketLimiter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OtpRateLimiter extends KeyedBucketLimiter<Long> {

    public OtpRateLimiter() {
        super(Duration.ofMinutes(10), 50_000);
    }

    @Override
    protected Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(3).refillGreedy(3, Duration.ofMinutes(5)).build())
                .build();
    }

    public boolean isAllowed(Long userId) {
        return tryConsume(userId);
    }
}
