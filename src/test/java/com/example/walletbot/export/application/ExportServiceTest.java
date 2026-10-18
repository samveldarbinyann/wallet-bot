package com.example.walletbot.export.application;

import com.example.walletbot.export.domain.ExportConfirmDto;
import com.example.walletbot.export.domain.ExportRequestDto;
import com.example.walletbot.export.domain.ExportRevealDto;
import com.example.walletbot.export.infrastructure.ExportIntentStore;
import com.example.walletbot.export.infrastructure.ExportSessionStore;
import com.example.walletbot.export.infrastructure.ExportSessionStore.RevealOutcome;
import com.example.walletbot.export.infrastructure.ExportSessionStore.RevealResult;
import com.example.walletbot.notification.application.EmailService;
import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.platform.web.security.OtpVerifyRateLimiter;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.*;
import com.example.walletbot.shared.security.HashingService;
import com.example.walletbot.shared.security.JwtService;
import com.example.walletbot.user.application.OtpService;
import com.example.walletbot.user.application.UserStatusGuard;
import com.example.walletbot.user.domain.EmailOtpEntity;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportServiceTest {

    @Mock JwtService jwtService;
    @Mock OtpService otpService;
    @Mock EmailService emailService;
    @Mock WalletRepository walletRepo;
    @Mock ExportIntentStore intentStore;
    @Mock ExportSessionStore sessionStore;
    @Mock ExportRateLimiter rateLimiter;
    @Mock
    OtpVerifyRateLimiter otpVerifyRateLimiter;
    @Mock
    MonitoringService monitoring;
    @Mock HashingService hashingService;
    @Mock
    UserStatusGuard userStatusGuard;

    @InjectMocks ExportService exportService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setTelegramId(100L);
        user.setEmail("user@example.com");
        user.setEmailVerifiedAt(Instant.now());
        user.setPinEnabled(true);
        user.setTwoFaLoginEnabled(true);
        user.setTokenVersion(0);
        user.setLang("en");
        when(otpVerifyRateLimiter.isAllowed(1L)).thenReturn(true);
    }

    @Test
    void request_success() {
        when(rateLimiter.isAllowed(1L)).thenReturn(true);
        when(otpService.generateCode()).thenReturn("123456");
        when(hashingService.hashOtp("123456")).thenReturn("hash");
        when(jwtService.generateExportPendingToken(eq(1L), anyString())).thenReturn("pending-token");

        ExportRequestDto resp = exportService.request(user);

        assertThat(resp.exportPendingToken()).isEqualTo("pending-token");
        verify(intentStore).issue(anyString());
        verify(emailService).sendExportOtp(eq("user@example.com"), eq("123456"), eq("en"));
    }

    @Test
    void request_noEmail_throws400() {
        user.setEmail(null);
        user.setEmailVerifiedAt(null);
        assertThatThrownBy(() -> exportService.request(user))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void request_rateLimited_throws429() {
        when(rateLimiter.isAllowed(1L)).thenReturn(false);
        assertThatThrownBy(() -> exportService.request(user))
                .isInstanceOf(RateLimitException.class);
        verify(monitoring).incrementExportFailedRateLimit();
    }

    @Test
    void request_cooldownActive_doesNotConsumeRateLimit() {
        doThrow(new RateLimitException("cooldown"))
                .when(otpService).enforceResendCooldown(1L, EmailOtpEntity.OtpPurpose.EXPORT_KEYS);

        assertThatThrownBy(() -> exportService.request(user))
                .isInstanceOf(RateLimitException.class);

        verify(rateLimiter, never()).isAllowed(anyLong());
        verify(monitoring, never()).incrementExportFailedRateLimit();
    }

    @Test
    void confirm_success() {
        Claims claims = mockClaims("1", "abc-jti");
        when(jwtService.parseExportPending("pending-token")).thenReturn(claims);
        when(intentStore.peek("abc-jti")).thenReturn(ExportIntentStore.State.ISSUED);
        when(intentStore.consume("abc-jti")).thenReturn(ExportIntentStore.State.ISSUED);

        WalletEntity wallet = wallet(10L, BlockchainNetwork.ETH, "0xabc");
        when(walletRepo.findByUserEntityId(1L)).thenReturn(List.of(wallet));
        when(jwtService.generateExportSessionToken(eq(1L), eq(0), anyString())).thenReturn("session-token");

        ExportConfirmDto resp = exportService.confirm(user, "pending-token", "123456");

        assertThat(resp.exportSessionToken()).isEqualTo("session-token");
        assertThat(resp.wallets()).hasSize(1);
        assertThat(resp.wallets().get(0).network()).isEqualTo("ETH");
        verify(sessionStore).open(anyString(), eq(1L), eq(0));
    }

    @Test
    void confirm_verifyRateLimited_throws429AndSkipsOtp() {
        Claims claims = mockClaims("1", "abc-jti");
        when(jwtService.parseExportPending("pending-token")).thenReturn(claims);
        when(intentStore.peek("abc-jti")).thenReturn(ExportIntentStore.State.ISSUED);
        when(otpVerifyRateLimiter.isAllowed(1L)).thenReturn(false);

        assertThatThrownBy(() -> exportService.confirm(user, "pending-token", "123456"))
                .isInstanceOf(RateLimitException.class);

        verify(otpService, never()).verifyOtp(any(), any(), any());
        verify(intentStore, never()).consume(anyString());
        verify(monitoring).incrementExportFailedRateLimit();
    }

    @Test
    void confirm_wrongOtpThenCorrect_succeedsWithSamePendingToken() {
        Claims claims = mockClaims("1", "abc-jti");
        when(jwtService.parseExportPending("pending-token")).thenReturn(claims);
        when(intentStore.peek("abc-jti")).thenReturn(ExportIntentStore.State.ISSUED);
        when(intentStore.consume("abc-jti")).thenReturn(ExportIntentStore.State.ISSUED);

        doThrow(new InvalidOtpException("Invalid code"))
                .when(otpService).verifyOtp(eq(user), eq("000000"), any());

        WalletEntity wallet = wallet(10L, BlockchainNetwork.ETH, "0xabc");
        when(walletRepo.findByUserEntityId(1L)).thenReturn(List.of(wallet));
        when(jwtService.generateExportSessionToken(eq(1L), eq(0), anyString())).thenReturn("session-token");

        assertThatThrownBy(() -> exportService.confirm(user, "pending-token", "000000"))
                .isInstanceOf(InvalidOtpException.class);

        ExportConfirmDto resp = exportService.confirm(user, "pending-token", "123456");
        assertThat(resp.exportSessionToken()).isEqualTo("session-token");
    }

    @Test
    void confirm_replayJti_throws400() {
        Claims claims = mockClaims("1", "abc-jti");
        when(jwtService.parseExportPending("pending-token")).thenReturn(claims);
        when(intentStore.peek("abc-jti")).thenReturn(ExportIntentStore.State.CONSUMED);

        assertThatThrownBy(() -> exportService.confirm(user, "pending-token", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.export_token_used_or_expired");
        verify(monitoring).incrementExportFailedBurned();
        verify(otpService, never()).verifyOtp(any(), any(), any());
    }

    @Test
    void confirm_subMismatch_throws400() {
        Claims claims = mockClaims("999", "abc-jti");
        when(jwtService.parseExportPending("pending-token")).thenReturn(claims);

        assertThatThrownBy(() -> exportService.confirm(user, "pending-token", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.token_user_mismatch");
    }

    @Test
    void confirm_invalidPendingToken_logsTokenInvalidMetric() {
        when(jwtService.parseExportPending("bad-token"))
                .thenThrow(new BadRequestException("error.export_pending_token_invalid"));

        assertThatThrownBy(() -> exportService.confirm(user, "bad-token", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.export_token_invalid");

        verify(monitoring).incrementExportFailedTokenInvalid();
        verify(intentStore, never()).consume(anyString());
    }

    @Test
    void confirm_wrongOtp_doesNotConsumeJti() {
        Claims claims = mockClaims("1", "abc-jti");
        when(jwtService.parseExportPending("pending-token")).thenReturn(claims);
        when(intentStore.peek("abc-jti")).thenReturn(ExportIntentStore.State.ISSUED);
        doThrow(new InvalidOtpException("Invalid code"))
                .when(otpService).verifyOtp(eq(user), eq("bad-otp"), any());

        assertThatThrownBy(() -> exportService.confirm(user, "pending-token", "bad-otp"))
                .isInstanceOf(InvalidOtpException.class);
        verify(intentStore, never()).consume(anyString());
        verify(monitoring).incrementExportFailedOtp();
    }

    @Test
    void reveal_userFrozen_throws403() {
        doThrow(new AccountFrozenException("frozen by admin"))
                .when(userStatusGuard).requireCanTransact(user);

        assertThatThrownBy(() -> exportService.reveal(user, "session-token", 10L))
                .isInstanceOf(AccountFrozenException.class);

        verifyNoInteractions(sessionStore);
    }

    @Test
    void reveal_invalidSessionToken_logsTokenInvalidMetric() {
        when(jwtService.parseExportSession("bad-token"))
                .thenThrow(new BadRequestException("error.export_session_token_invalid"));

        assertThatThrownBy(() -> exportService.reveal(user, "bad-token", 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.export_session_token_invalid_or_expired");

        verify(monitoring).incrementExportFailedTokenInvalid();
        verify(sessionStore, never()).tryReveal(anyString(), anyLong(), anyInt());
    }

    @Test
    void reveal_success() {
        Claims claims = mockSessionClaims("1", "sid-123", 0);
        when(jwtService.parseExportSession("session-token")).thenReturn(claims);

        when(sessionStore.tryReveal("sid-123", 1L, 0))
                .thenReturn(new RevealOutcome(RevealResult.OK));

        WalletEntity wallet = wallet(10L, BlockchainNetwork.BTC, "bc1q...");
        wallet.setPrivateKey("WIF_KEY_HERE");
        when(walletRepo.findByIdAndUserEntityId(10L, 1L)).thenReturn(Optional.of(wallet));

        ExportRevealDto resp = exportService.reveal(user, "session-token", 10L);

        assertThat(resp.privateKey()).isEqualTo("WIF_KEY_HERE");
        assertThat(resp.network()).isEqualTo("BTC");
        assertThat(resp.format()).isEqualTo("WIF");
        assertThat(resp.toString()).doesNotContain("WIF_KEY_HERE");
        assertThat(resp.toString()).contains("privateKey=***");
    }

    @Test
    void reveal_foreignWalletId_throws404() {
        Claims claims = mockSessionClaims("1", "sid-123", 0);
        when(jwtService.parseExportSession("session-token")).thenReturn(claims);

        when(sessionStore.tryReveal("sid-123", 1L, 0))
                .thenReturn(new RevealOutcome(RevealResult.OK));
        when(walletRepo.findByIdAndUserEntityId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exportService.reveal(user, "session-token", 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reveal_tokenNetworkBnb_throws400() {
        Claims claims = mockSessionClaims("1", "sid-123", 0);
        when(jwtService.parseExportSession("session-token")).thenReturn(claims);

        when(sessionStore.tryReveal("sid-123", 1L, 0))
                .thenReturn(new RevealOutcome(RevealResult.OK));

        WalletEntity wallet = wallet(10L, BlockchainNetwork.BNB, "0xabc");
        when(walletRepo.findByIdAndUserEntityId(10L, 1L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> exportService.reveal(user, "session-token", 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.export_network_not_exportable");
    }

    @Test
    void reveal_tokenVersionMismatch_throws400() {
        user.setTokenVersion(1);
        Claims claims = mockSessionClaims("1", "sid-123", 0);
        when(jwtService.parseExportSession("session-token")).thenReturn(claims);

        assertThatThrownBy(() -> exportService.reveal(user, "session-token", 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.export_session_invalidated");
        verify(sessionStore).burn("sid-123");
    }

    @Test
    void reveal_burnedSession_throws400() {
        Claims claims = mockSessionClaims("1", "sid-123", 0);
        when(jwtService.parseExportSession("session-token")).thenReturn(claims);
        when(sessionStore.tryReveal("sid-123", 1L, 0))
                .thenReturn(new RevealOutcome(RevealResult.EXPIRED));

        assertThatThrownBy(() -> exportService.reveal(user, "session-token", 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.export_session_expired");
        verify(monitoring).incrementExportFailedBurned();
    }

    @Test
    void reveal_wrongUser_logsHijackAndThrows() {
        Claims claims = mockSessionClaims("1", "sid-123", 0);
        when(jwtService.parseExportSession("session-token")).thenReturn(claims);
        when(sessionStore.tryReveal("sid-123", 1L, 0))
                .thenReturn(new RevealOutcome(RevealResult.WRONG_USER));

        assertThatThrownBy(() -> exportService.reveal(user, "session-token", 10L))
                .isInstanceOf(BadRequestException.class);
        verify(monitoring).incrementExportFailedBurned();
    }

    @Test
    void close_validToken_burnsSid() {
        Claims claims = mockSessionClaims("1", "sid-abc", 0);
        when(jwtService.parseExportSession("session-token")).thenReturn(claims);

        exportService.close(user, "session-token");

        verify(sessionStore).burn("sid-abc");
    }

    @Test
    void close_invalidToken_ignoresSilently() {
        when(jwtService.parseExportSession("bad-token")).thenThrow(new BadRequestException("invalid"));
        exportService.close(user, "bad-token");
        verifyNoInteractions(sessionStore);
    }

    @Test
    void close_unexpectedException_doesNotPropagate() {
        Claims claims = mockSessionClaims("1", "sid-abc", 0);
        when(jwtService.parseExportSession("session-token")).thenReturn(claims);
        doThrow(new RuntimeException("cache down")).when(sessionStore).burn("sid-abc");

        exportService.close(user, "session-token");
    }

    private Claims mockClaims(String sub, String jti) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(sub);
        when(claims.get("jti", String.class)).thenReturn(jti);
        return claims;
    }

    private Claims mockSessionClaims(String sub, String sid, int tv) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(sub);
        when(claims.get("sid", String.class)).thenReturn(sid);
        when(claims.get("tv", Integer.class)).thenReturn(tv);
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(120)));
        return claims;
    }

    private WalletEntity wallet(Long id, BlockchainNetwork network, String address) {
        WalletEntity w = new WalletEntity();
        w.setId(id);
        w.setNetwork(network);
        w.setAddress(address);
        return w;
    }
}
