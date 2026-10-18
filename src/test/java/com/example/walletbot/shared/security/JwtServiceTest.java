package com.example.walletbot.shared.security;

import com.example.walletbot.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String ACCESS_SECRET = "test-jwt-secret-key-at-least-32-chars-long!!";
    private static final String INTENT_SECRET = "test-intent-secret-key-at-least-32-chars-long!!";
    private static final long EXPIRY_MINUTES = 4;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(ACCESS_SECRET, INTENT_SECRET, EXPIRY_MINUTES);
    }

    @Test
    void constructor_shortSecret_throwsIllegalArgument() {
        assertThatThrownBy(() -> new JwtService("short", INTENT_SECRET, EXPIRY_MINUTES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_shortIntentSecret_throwsIllegalArgument() {
        assertThatThrownBy(() -> new JwtService(ACCESS_SECRET, "short", EXPIRY_MINUTES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseAccess_validToken_extractsAllFields() {
        String token = jwtService.generateToken(42L, 100L, 7, "ADMIN");
        var parsed = jwtService.parseAccess(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().userId()).isEqualTo(42L);
        assertThat(parsed.get().tokenVersion()).isEqualTo(7);
        assertThat(parsed.get().role()).isEqualTo("ADMIN");
    }

    @Test
    void parseAccess_defaultRole_isUser() {
        String token = jwtService.generateToken(1L, 100L, 1);
        var parsed = jwtService.parseAccess(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().role()).isEqualTo("USER");
    }

    @Test
    void isValid_accessToken_returnsTrue() {
        String token = jwtService.generateToken(1L, 100L, 1);
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void isValid_pendingToken_returnsFalse() {
        String token = jwtService.generatePendingToken(1L, 100L);
        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void isValid_pinPendingToken_returnsFalse() {
        String token = jwtService.generatePinPendingToken(1L, 100L);
        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void isValid_exportPendingToken_returnsFalse() {
        String token = jwtService.generateExportPendingToken(1L, "jti-1");
        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void isValid_exportSessionToken_returnsFalse() {
        String token = jwtService.generateExportSessionToken(1L, 1, "sid-1");
        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void isValid_wrongSignature_returnsFalse() {
        String intentToken = jwtService.generatePendingToken(1L, 100L);
        assertThat(jwtService.isValid(intentToken)).isFalse();
    }

    @Test
    void isValid_garbage_returnsFalse() {
        assertThat(jwtService.isValid("not.a.token")).isFalse();
    }

    @Test
    void pendingToken_validateWithCorrectUser_passes() {
        String token = jwtService.generatePendingToken(5L, 200L);
        jwtService.validatePendingToken(token, 5L);
    }

    @Test
    void pendingToken_validateWithWrongUser_throwsBadRequest() {
        String token = jwtService.generatePendingToken(5L, 200L);
        assertThatThrownBy(() -> jwtService.validatePendingToken(token, 99L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void pendingToken_extractUserId_roundTrip() {
        String token = jwtService.generatePendingToken(5L, 200L);
        assertThat(jwtService.extractUserIdFromPendingToken(token)).isEqualTo(5L);
    }

    @Test
    void accessToken_usedAsPendingToken_throwsBadRequest() {
        String token = jwtService.generateToken(1L, 100L, 1);
        assertThatThrownBy(() -> jwtService.extractUserIdFromPendingToken(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    void exportPendingToken_parseExportPending_passes() {
        String token = jwtService.generateExportPendingToken(7L, "jti-abc");
        var claims = jwtService.parseExportPending(token);
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("jti", String.class)).isEqualTo("jti-abc");
    }

    @Test
    void exportSessionToken_parseExportSession_passes() {
        String token = jwtService.generateExportSessionToken(7L, 2, "sid-xyz");
        var claims = jwtService.parseExportSession(token);
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("sid", String.class)).isEqualTo("sid-xyz");
    }

    @Test
    void exportPendingToken_parsedAsSession_throwsBadRequest() {
        String token = jwtService.generateExportPendingToken(7L, "jti");
        assertThatThrownBy(() -> jwtService.parseExportSession(token))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void pinPendingToken_extractUserId_roundTrip() {
        String token = jwtService.generatePinPendingToken(9L, 300L);
        assertThat(jwtService.extractUserIdFromPinPendingToken(token)).isEqualTo(9L);
    }
}
