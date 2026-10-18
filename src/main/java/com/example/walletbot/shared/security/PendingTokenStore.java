package com.example.walletbot.shared.security;

import com.example.walletbot.shared.exception.BadRequestException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class PendingTokenStore {

    private final Cache<String, Boolean> consumed = Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .maximumSize(200_000)
            .build();

    public void consumeOrThrow(String jti) {
        if (jti == null || jti.isBlank()) {
            throw new BadRequestException("error.pending_token_used");
        }
        if (consumed.asMap().putIfAbsent(jti, Boolean.TRUE) != null) {
            throw new BadRequestException("error.pending_token_used");
        }
    }
}
