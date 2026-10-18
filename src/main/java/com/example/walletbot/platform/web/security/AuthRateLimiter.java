package com.example.walletbot.platform.web.security;

import com.example.walletbot.shared.ratelimit.KeyedBucketLimiter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthRateLimiter extends KeyedBucketLimiter<String> {

    private final int trustedProxyCount;

    public AuthRateLimiter(@Value("${security.trusted-proxy-count:0}") int trustedProxyCount) {
        super(Duration.ofMinutes(5), 10_000);
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
    }

    @Override
    protected Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build())
                .build();
    }

    public boolean isAllowed(HttpServletRequest request) {
        return tryConsume(extractClientIp(request));
    }

    private String extractClientIp(HttpServletRequest request) {
        if (trustedProxyCount == 0) {
            return request.getRemoteAddr();
        }
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] parts = xfHeader.split(",");
        int idx = parts.length - trustedProxyCount;
        if (idx < 0) idx = 0;
        return parts[idx].trim();
    }
}
