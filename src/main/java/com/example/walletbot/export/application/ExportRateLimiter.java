package com.example.walletbot.export.application;

import com.example.walletbot.shared.ratelimit.KeyedBucketLimiter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ExportRateLimiter extends KeyedBucketLimiter<Long> {

    public ExportRateLimiter() {
        super(Duration.ofHours(25), 50_000);
    }

    @Override
    protected Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(10)
                        .refillIntervally(10, Duration.ofHours(24))
                        .build())
                .build();
    }

    public boolean isAllowed(Long userId) {
        return tryConsume(userId);
    }
}
