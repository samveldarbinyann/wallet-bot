package com.example.walletbot.user.application;

import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.security.HashingService;
import com.example.walletbot.user.domain.RefreshTokenEntity;
import com.example.walletbot.user.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repo;
    private final HashingService hashingService;

    @Value("${jwt.refresh-ttl-days:30}")
    private long refreshTtlDays;

    @Transactional
    public Issued issue(Long userId) {
        return persist(userId);
    }

    @Transactional(readOnly = true)
    public Long validate(String rawToken) {
        RefreshTokenEntity stored = repo.findByTokenHash(hashingService.sha256Hex(rawToken))
                .orElseThrow(() -> new BadRequestException("error.invalid_refresh_token"));
        if (stored.getRevokedAt() != null) {
            throw new BadRequestException("error.refresh_token_revoked");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("error.refresh_token_expired");
        }
        return stored.getUserId();
    }

    @Transactional
    public void revokeAll(Long userId) {
        repo.revokeAllActiveForUser(userId, Instant.now());
    }

    private Issued persist(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofDays(refreshTtlDays));
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(userId);
        entity.setTokenHash(hashingService.sha256Hex(raw));
        entity.setIssuedAt(now);
        entity.setExpiresAt(expires);
        repo.saveAndFlush(entity);
        return new Issued(raw, expires);
    }

    public record Issued(String rawToken, Instant expiresAt) {
    }
}
