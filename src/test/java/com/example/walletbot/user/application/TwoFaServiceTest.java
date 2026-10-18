package com.example.walletbot.user.application;

import com.example.walletbot.notification.application.EmailService;
import com.example.walletbot.platform.web.security.OtpRateLimiter;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.shared.exception.TwoFaRequiredException;
import com.example.walletbot.shared.security.HashingService;
import com.example.walletbot.shared.security.JwtService;
import com.example.walletbot.shared.security.PendingTokenStore;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.infrastructure.UserCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose.LOGIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwoFaServiceTest {

    @Mock
    EmailService emailService;
    @Mock
    JwtService jwtService;
    @Mock
    UserCacheService userCacheService;
    @Mock
    HashingService hashingService;
    @Mock
    OtpService otpService;
    @Mock
    OtpRateLimiter otpRateLimiter;
    @Mock
    PendingTokenStore pendingTokenStore;
    @Mock
    RefreshTokenService refreshTokenService;

    @InjectMocks
    TwoFaService service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setTelegramId(100L);
        user.setTokenVersion(0);
        user.setEmail("sam@example.com");

        when(otpService.generateCode()).thenReturn("123456");
        when(hashingService.hashOtp("123456")).thenReturn("HASH");
        when(otpRateLimiter.isAllowed(any())).thenReturn(true);
        when(jwtService.generateToken(any(), any(), anyInt(), any())).thenReturn("ACCESS");
        when(refreshTokenService.issue(any())).thenReturn(new RefreshTokenService.Issued("REFRESH", Instant.now()));
    }

    @Test
    void sendLoginOtp_inCooldown_doesNothing() {
        when(otpService.isInResendCooldown(1L, LOGIN)).thenReturn(true);

        service.sendLoginOtp(user);

        verify(otpService, never()).persistOtp(any(), any(), any());
        verify(emailService, never()).sendOtp(any(), any(), any());
    }

    @Test
    void sendLoginOtp_rateLimited_throwsRateLimit() {
        when(otpService.isInResendCooldown(1L, LOGIN)).thenReturn(false);
        when(otpRateLimiter.isAllowed(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.sendLoginOtp(user))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void sendLoginOtp_allowed_persistsAndSends() {
        when(otpService.isInResendCooldown(1L, LOGIN)).thenReturn(false);

        service.sendLoginOtp(user);

        verify(otpService).persistOtp(user, "HASH", LOGIN);
        verify(emailService).sendOtp(eq("sam@example.com"), eq("123456"), any());
    }

    @Test
    void verifyLoginOtp_validates_consumesPendingToken_mintsSession() {
        when(jwtService.jtiFromPendingToken("pending")).thenReturn("jti-1");

        TwoFaService.IssuedSession session = service.verifyLoginOtp(user, "pending", "123456");

        verify(jwtService).validatePendingToken("pending", 1L);
        verify(otpService).verifyOtp(user, "123456", LOGIN);
        verify(pendingTokenStore).consumeOrThrow("jti-1");
        assertThat(session.token()).isEqualTo("ACCESS");
        assertThat(session.refreshToken()).isEqualTo("REFRESH");
    }

    @Test
    void issueTokenOrGate_twoFaEnabled_gatesWithPendingToken() {
        user.setTwoFaLoginEnabled(true);
        when(jwtService.generatePendingToken(1L, 100L)).thenReturn("pending");
        when(otpService.isInResendCooldown(1L, LOGIN)).thenReturn(false);

        assertThatThrownBy(() -> service.issueTokenOrGate(user))
                .isInstanceOf(TwoFaRequiredException.class);

        verify(emailService).sendOtp(eq("sam@example.com"), any(), any());
    }

    @Test
    void issueTokenOrGate_twoFaDisabled_mintsSession() {
        user.setTwoFaLoginEnabled(false);

        TwoFaService.IssuedSession session = service.issueTokenOrGate(user);

        assertThat(session.token()).isEqualTo("ACCESS");
        verify(refreshTokenService).issue(1L);
    }

    @Test
    void resolveAuthSession_pinEnabledNoTwoFa_returnsPinRequired() {
        user.setPinEnabled(true);
        user.setTwoFaLoginEnabled(false);
        when(jwtService.generatePinPendingToken(1L, 100L)).thenReturn("pin-pending");

        TwoFaService.AuthSessionResult result = service.resolveAuthSession(user);

        assertThat(result).isInstanceOf(TwoFaService.AuthSessionResult.PinRequired.class);
        TwoFaService.AuthSessionResult.PinRequired pin = (TwoFaService.AuthSessionResult.PinRequired) result;
        assertThat(pin.pinPendingToken()).isEqualTo("pin-pending");
        assertThat(pin.maskedEmail()).isNotBlank();
    }

    @Test
    void resolveAuthSession_noPin_returnsTokenIssued() {
        user.setPinEnabled(false);
        user.setTwoFaLoginEnabled(false);

        TwoFaService.AuthSessionResult result = service.resolveAuthSession(user);

        assertThat(result).isInstanceOf(TwoFaService.AuthSessionResult.TokenIssued.class);
    }
}
