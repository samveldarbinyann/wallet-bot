package com.example.walletbot.platform.web.security;

import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.util.HexUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class TelegramAuthService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long MAX_AUTH_AGE_SECONDS = 60;

    private final byte[] secretKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, Boolean> seenInitData = Caffeine.newBuilder()
            .expireAfterWrite(MAX_AUTH_AGE_SECONDS + 5, TimeUnit.SECONDS)
            .maximumSize(100_000)
            .build();

    public TelegramAuthService(@Value("${telegram.bot.token}") String botToken) {
        this.secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
    }

    public TelegramAuthResult validate(String initData) {
        if (initData == null || initData.isBlank()) {
            throw new BadRequestException("error.auth_init_data_empty");
        }

        Map<String, String> params = parseQueryString(initData);
        String hash = params.remove("hash");
        if (hash == null || hash.isBlank()) {
            throw new BadRequestException("error.auth_init_data_missing_hash");
        }

        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder dataCheckString = new StringBuilder();
        for (var entry : sorted.entrySet()) {
            if (!dataCheckString.isEmpty()) dataCheckString.append('\n');
            dataCheckString.append(entry.getKey()).append('=').append(entry.getValue());
        }

        byte[] computedHash = hmacSha256(secretKey, dataCheckString.toString().getBytes(StandardCharsets.UTF_8));
        byte[] providedHash = HexUtils.hexToBytes(hash);

        if (!MessageDigest.isEqual(computedHash, providedHash)) {
            log.warn("Invalid initData signature. Computed hash mismatch.");
            throw new BadRequestException("error.auth_init_data_invalid_signature");
        }

        String authDateStr = params.get("auth_date");
        if (authDateStr == null) {
            throw new BadRequestException("error.auth_init_data_missing_auth_date");
        }
        try {
            long authDate = Long.parseLong(authDateStr);
            long now = Instant.now().getEpochSecond();
            if (now - authDate > MAX_AUTH_AGE_SECONDS || authDate - now > 30) {
                throw new BadRequestException("error.auth_init_data_expired");
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("error.auth_init_data_invalid_auth_date");
        }

        if (seenInitData.asMap().putIfAbsent(hash, Boolean.TRUE) != null) {
            throw new BadRequestException("error.auth_init_data_replay");
        }

        String userJson = params.get("user");
        if (userJson == null) {
            throw new BadRequestException("error.auth_init_data_missing_user");
        }

        try {
            JsonNode userNode = objectMapper.readTree(userJson);
            long telegramId = userNode.get("id").asLong();
            String username = getOptionalField(userNode, "username");
            String firstName = getOptionalField(userNode, "first_name");
            String lastName = getOptionalField(userNode, "last_name");
            String languageCode = getOptionalField(userNode, "language_code");
            return new TelegramAuthResult(telegramId, username, firstName, lastName, languageCode);
        } catch (Exception e) {
            throw new BadRequestException("error.auth_init_data_parse_failed");
        }
    }

    public record TelegramAuthResult(long telegramId, String username, String firstName, String lastName,
                                     String languageCode) {
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new TreeMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 error", e);
        }
    }

    private static String getOptionalField(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText(null) : null;
    }

}
