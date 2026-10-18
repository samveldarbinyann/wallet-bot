package com.example.walletbot.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class IntentTokenCodec {

    private final SecretKey key;

    public IntentTokenCodec(@Value("${intent.secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("intent.secret length must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public JwtBuilder builder() {
        return Jwts.builder().signWith(key);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
