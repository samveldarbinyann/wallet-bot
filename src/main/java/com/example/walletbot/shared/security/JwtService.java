package com.example.walletbot.shared.security;

import com.example.walletbot.shared.exception.BadRequestException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final String PENDING_CLAIM = "pending";
    private static final long PENDING_TTL_MINUTES = 15;
    private static final String PIN_PENDING_CLAIM = "pinPending";
    private static final long PIN_PENDING_TTL_MINUTES = 10;
    private static final long EXPORT_PENDING_TTL_MINUTES = 5;
    private static final long EXPORT_SESSION_TTL_MINUTES = 2;

    private final SecretKey accessKey;
    private final SecretKey intentKey;
    private final JwtParser accessParser;
    private final JwtParser intentParser;
    private final long expirationMinutes;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${intent.secret}") String intentSecret,
                      @Value("${jwt.expiration-minutes:15}") long expirationMinutes) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Secret length must be at least 32 characters");
        }
        if (intentSecret == null || intentSecret.length() < 32) {
            throw new IllegalArgumentException("intent.secret length must be at least 32 characters");
        }
        this.accessKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.intentKey = Keys.hmacShaKeyFor(intentSecret.getBytes(StandardCharsets.UTF_8));
        this.accessParser = Jwts.parser().verifyWith(accessKey).build();
        this.intentParser = Jwts.parser().verifyWith(intentKey).build();
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(Long userId, Long telegramId, int tokenVersion) {
        return generateToken(userId, telegramId, tokenVersion, "USER");
    }

    public String generateToken(Long userId, Long telegramId, int tokenVersion, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("telegramId", telegramId)
                .claim("ver", tokenVersion)
                .claim("typ", "access")
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(accessKey)
                .compact();
    }

    public String generatePendingToken(Long userId, Long telegramId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("telegramId", telegramId)
                .claim(PENDING_CLAIM, true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(PENDING_TTL_MINUTES, ChronoUnit.MINUTES)))
                .signWith(intentKey)
                .compact();
    }

    public void validatePendingToken(String token, Long expectedUserId) {
        Claims claims = parseIntentClaims(token);
        if (!Boolean.TRUE.equals(claims.get(PENDING_CLAIM, Boolean.class)))
            throw new BadRequestException("error.pending_token_invalid");
        Long userId = Long.parseLong(claims.getSubject());
        if (!userId.equals(expectedUserId))
            throw new BadRequestException("error.token_user_mismatch");
    }

    public Long extractUserIdFromPendingToken(String token) {
        Claims claims = parseIntentClaims(token);
        if (!Boolean.TRUE.equals(claims.get(PENDING_CLAIM, Boolean.class)))
            throw new BadRequestException("error.pending_token_invalid");
        return Long.parseLong(claims.getSubject());
    }

    public String jtiFromPendingToken(String token) {
        return parseIntentClaims(token).getId();
    }

    public String generatePinPendingToken(Long userId, Long telegramId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("telegramId", telegramId)
                .claim(PIN_PENDING_CLAIM, true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(PIN_PENDING_TTL_MINUTES, ChronoUnit.MINUTES)))
                .signWith(intentKey)
                .compact();
    }

    public Long extractUserIdFromPinPendingToken(String token) {
        Claims claims = parseIntentClaims(token);
        if (!Boolean.TRUE.equals(claims.get(PIN_PENDING_CLAIM, Boolean.class)))
            throw new BadRequestException("error.pin_pending_token_invalid");
        return Long.parseLong(claims.getSubject());
    }

    public String generateExportPendingToken(Long userId, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("typ", "exportPending")
                .claim("jti", jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(EXPORT_PENDING_TTL_MINUTES, ChronoUnit.MINUTES)))
                .signWith(intentKey)
                .compact();
    }

    public Claims parseExportPending(String token) {
        Claims claims = parseIntentClaims(token);
        if (!"exportPending".equals(claims.get("typ", String.class)))
            throw new BadRequestException("error.export_pending_token_invalid");
        String jti = claims.get("jti", String.class);
        if (jti == null || jti.isBlank())
            throw new BadRequestException("error.export_pending_token_invalid");
        return claims;
    }

    public String generateExportSessionToken(Long userId, int tokenVersion, String sid) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("typ", "exportSession")
                .claim("sid", sid)
                .claim("tv", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(EXPORT_SESSION_TTL_MINUTES, ChronoUnit.MINUTES)))
                .signWith(intentKey)
                .compact();
    }

    public Claims parseExportSession(String token) {
        Claims claims = parseIntentClaims(token);
        if (!"exportSession".equals(claims.get("typ", String.class)))
            throw new BadRequestException("error.export_session_token_invalid");
        String sid = claims.get("sid", String.class);
        if (sid == null || sid.isBlank())
            throw new BadRequestException("error.export_session_token_invalid");
        if (claims.get("tv", Integer.class) == null)
            throw new BadRequestException("error.export_session_token_invalid");
        return claims;
    }

    public boolean isProofOfSession(String token, Long expectedUserId) {
        try {
            Claims claims;
            try {
                claims = parseAccessClaims(token);
            } catch (ExpiredJwtException e) {
                claims = e.getClaims();
            }
            if (!"access".equals(claims.get("typ", String.class))) return false;
            return expectedUserId.toString().equals(claims.getSubject());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isValid(String token) {
        return parseAccess(token).isPresent();
    }

    public Optional<AccessToken> parseAccess(String token) {
        try {
            Claims claims = parseAccessClaims(token);
            if (Boolean.TRUE.equals(claims.get(PENDING_CLAIM, Boolean.class))) return Optional.empty();
            if (Boolean.TRUE.equals(claims.get(PIN_PENDING_CLAIM, Boolean.class))) return Optional.empty();
            if (!"access".equals(claims.get("typ", String.class))) return Optional.empty();
            String subject = claims.getSubject();
            if (subject == null) return Optional.empty();
            if (!claims.getExpiration().after(new Date())) return Optional.empty();
            String role = claims.get("role", String.class);
            return Optional.of(new AccessToken(
                    Long.parseLong(subject),
                    claims.get("ver", Integer.class),
                    role != null ? role : "USER"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Claims parseAccessClaims(String token) {
        return accessParser.parseSignedClaims(token).getPayload();
    }

    private Claims parseIntentClaims(String token) {
        return intentParser.parseSignedClaims(token).getPayload();
    }

    public record AccessToken(long userId, Integer tokenVersion, String role) {
    }
}