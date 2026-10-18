package com.example.walletbot.wallet.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

@Component
public class QrShareTokenStore {

    private static final Duration TTL = Duration.ofHours(1);
    private static final int TOKEN_BYTES = 24;

    private final SecureRandom random = new SecureRandom();
    private final Cache<String, String> tokenToAddress = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(10_000)
            .build();

    public String store(String address) {
        byte[] buf = new byte[TOKEN_BYTES];
        random.nextBytes(buf);
        String token = HexFormat.of().formatHex(buf);
        tokenToAddress.put(token, address);
        return token;
    }

    public String resolve(String token) {
        return tokenToAddress.getIfPresent(token);
    }
}
