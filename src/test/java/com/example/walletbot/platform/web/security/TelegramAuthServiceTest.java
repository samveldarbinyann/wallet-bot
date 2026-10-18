package com.example.walletbot.platform.web.security;

import com.example.walletbot.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramAuthServiceTest {

    private static final String BOT_TOKEN = "123456:test-bot-token";

    private TelegramAuthService service;

    private static String buildSignedInitData(long authDate, String userJson) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("auth_date", String.valueOf(authDate));
        params.put("user", userJson);

        StringBuilder dcs = new StringBuilder();
        for (var e : params.entrySet()) {
            if (!dcs.isEmpty()) dcs.append('\n');
            dcs.append(e.getKey()).append('=').append(e.getValue());
        }
        byte[] secretKey = hmac("WebAppData".getBytes(StandardCharsets.UTF_8),
                BOT_TOKEN.getBytes(StandardCharsets.UTF_8));
        String hash = toHex(hmac(secretKey, dcs.toString().getBytes(StandardCharsets.UTF_8)));

        return "auth_date=" + authDate + "&user=" + enc(userJson) + "&hash=" + hash;
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        service = new TelegramAuthService(BOT_TOKEN);
    }

    @Test
    void validate_validInitData_returnsResult() {
        String userJson = "{\"id\":42,\"username\":\"sam\",\"first_name\":\"Sam\",\"last_name\":\"D\",\"language_code\":\"ru\"}";
        String initData = buildSignedInitData(Instant.now().getEpochSecond(), userJson);

        TelegramAuthService.TelegramAuthResult result = service.validate(initData);

        assertThat(result.telegramId()).isEqualTo(42L);
        assertThat(result.username()).isEqualTo("sam");
        assertThat(result.firstName()).isEqualTo("Sam");
        assertThat(result.lastName()).isEqualTo("D");
        assertThat(result.languageCode()).isEqualTo("ru");
    }

    @Test
    void validate_emptyInitData_throws() {
        assertThatThrownBy(() -> service.validate("  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.auth_init_data_empty");
    }

    @Test
    void validate_missingHash_throws() {
        long now = Instant.now().getEpochSecond();
        String initData = "auth_date=" + now + "&user=" + enc("{\"id\":1}");

        assertThatThrownBy(() -> service.validate(initData))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.auth_init_data_missing_hash");
    }

    @Test
    void validate_tamperedSignature_throws() {
        String userJson = "{\"id\":42}";
        long now = Instant.now().getEpochSecond();
        String initData = "auth_date=" + now + "&user=" + enc(userJson) + "&hash=deadbeef";

        assertThatThrownBy(() -> service.validate(initData))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.auth_init_data_invalid_signature");
    }

    @Test
    void validate_expiredAuthDate_throws() {
        String userJson = "{\"id\":42}";
        long stale = Instant.now().getEpochSecond() - 120;
        String initData = buildSignedInitData(stale, userJson);

        assertThatThrownBy(() -> service.validate(initData))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.auth_init_data_expired");
    }

    @Test
    void validate_replayedInitData_throwsOnSecondUse() {
        String userJson = "{\"id\":42}";
        String initData = buildSignedInitData(Instant.now().getEpochSecond(), userJson);

        service.validate(initData);

        assertThatThrownBy(() -> service.validate(initData))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.auth_init_data_replay");
    }

    @Test
    void validate_wrongBotToken_signatureRejected() {
        TelegramAuthService other = new TelegramAuthService("999:another-token");
        String userJson = "{\"id\":42}";
        String initData = buildSignedInitData(Instant.now().getEpochSecond(), userJson);

        assertThatThrownBy(() -> other.validate(initData))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.auth_init_data_invalid_signature");
    }
}
