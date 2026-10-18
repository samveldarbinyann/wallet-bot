package com.example.walletbot.shared.exception;

import lombok.Getter;

@Getter
public class RateLimitException extends RuntimeException {

    private static final long DEFAULT_RETRY_AFTER_SECONDS = 60;

    private final long retryAfterSeconds;

    public RateLimitException(String message) {
        this(message, DEFAULT_RETRY_AFTER_SECONDS);
    }

    public RateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
