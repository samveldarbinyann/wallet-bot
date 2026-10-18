package com.example.walletbot.user.application;

import com.example.walletbot.notification.application.EmailService;
import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.shared.constant.UserRole;
import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.InvalidOtpException;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.shared.security.HashingService;
import com.example.walletbot.shared.security.JwtService;
import com.example.walletbot.shared.security.PendingTokenStore;
import com.example.walletbot.user.domain.EmailOtpRepository;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import com.example.walletbot.user.infrastructure.UserCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose.EMAIL_BIND;
import static com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose.TWOFA_DISABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityServiceTest {

    private static final String PIN_PEPPER = "test-pin-pepper-32-characters-long!!";

    @Mock
    EmailOtpRepository otpRepo;
    @Mock
    UserRepository userRepo;
    @Mock
    EmailService emailService;
    @Mock
    JwtService jwtService;
    @Mock
    UserCacheService userCacheService;
    @Mock
    MonitoringService monitoringService;
    @Mock
    HashingService hashingService;
    @Mock
    OtpService otpService;
    @Mock
    PinLockoutService pinLockoutService;
    @Mock
    RefreshTokenService refreshTokenService;
    @Mock
    PendingTokenStore pendingTokenStore;
    @Mock
    UserStatusGuard userStatusGuard;

    @InjectMocks
    SecurityService securityService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(securityService, "pinPepper", PIN_PEPPER);

        user = new UserEntity();
        user.setId(1L);
        user.setTelegramId(100L);
        user.setTokenVersion(0);

        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hashingService.hashOtp(any())).thenReturn("HASH");
        when(otpService.generateCode()).thenReturn("123456");
        when(pinLockoutService.recordFailure(any())).thenReturn(Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    @Test
    void initiateEmailBinding_validEmail_sendsOtpAndSetsPending() {
        securityService.initiateEmailBinding(user, "  Sam@Example.COM  ");

        verify(otpService).enforceResendCooldown(1L, EMAIL_BIND);
        verify(otpService).persistOtp(eq(user), eq("HASH"), eq(EMAIL_BIND), eq("sam@example.com"));
        verify(emailService).sendOtp(eq("sam@example.com"), eq("123456"), any());
        assertThat(user.getPendingEmail()).isEqualTo("sam@example.com");
    }

    @Test
    void initiateEmailBinding_invalidFormat_throwsBadRequest() {
        assertThatThrownBy(() -> securityService.initiateEmailBinding(user, "not-an-email"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.invalid_email_format");
    }

    @Test
    void confirmEmailBinding_noPendingEmail_throwsBadRequest() {
        user.setPendingEmail(null);

        assertThatThrownBy(() -> securityService.confirmEmailBinding(user, "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.no_pending_email_binding");
    }

    @Test
    void confirmEmailBinding_valid_setsEmailAndEvictsCache() {
        user.setPendingEmail("sam@example.com");

        securityService.confirmEmailBinding(user, "123456");

        assertThat(user.getEmail()).isEqualTo("sam@example.com");
        assertThat(user.getPendingEmail()).isNull();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(userRepo).save(user);
        verify(userCacheService).evict(1L);
    }

    @Test
    void removeEmail_hasPinEnabled_throwsBadRequest() {
        user.setPinEnabled(true);

        assertThatThrownBy(() -> securityService.removeEmail(user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.disable_pin_2fa_before_email_unlink");
    }

    @Test
    void removeEmail_hasTwoFaLogin_throwsBadRequest() {
        user.setTwoFaLoginEnabled(true);

        assertThatThrownBy(() -> securityService.removeEmail(user))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void removeEmail_noPin_noTwoFa_clearsEmailAndIncreasesVersion() {
        user.setEmail("sam@example.com");
        user.setEmailVerifiedAt(Instant.now());

        securityService.removeEmail(user);

        assertThat(user.getEmail()).isNull();
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(otpRepo).deleteByUserId(1L);
        verify(userCacheService).evict(1L);
    }

    @Test
    void confirmPinSetup_noVerifiedEmail_throwsBadRequest() {
        user.setEmailVerifiedAt(null);

        assertThatThrownBy(() -> securityService.confirmPinSetup(user, "123456", "1234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.verify_email_first");
    }

    @Test
    void confirmPinSetup_pinAlreadyEnabled_throwsBadRequest() {
        user.setEmailVerifiedAt(Instant.now());
        user.setPinEnabled(true);

        assertThatThrownBy(() -> securityService.confirmPinSetup(user, "123456", "1234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.pin_already_set");
    }

    @Test
    void confirmPinSetup_verifiedEmail_setsHashAndEnabled() {
        user.setEmailVerifiedAt(Instant.now());

        securityService.confirmPinSetup(user, "123456", "1234");

        assertThat(user.getPinHash()).startsWith("{bcrypt}");
        assertThat(user.isPinEnabled()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(otpService).verifyOtp(eq(user), eq("123456"), eq(com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose.PIN_SETUP));
        verify(userRepo).save(user);
        verify(userCacheService).evict(1L);
    }

    @Test
    void verifyPin_lockedAccount_throwsRateLimit() {
        user.setPinLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        user.setPinHash("{bcrypt}anything");

        assertThatThrownBy(() -> securityService.verifyPin(user, "1234"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void verifyPin_correctBcryptPin_resetsLockout() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("1234" + PIN_PEPPER);
        user.setPinHash("{bcrypt}" + hash);
        user.setPinFailures(2);

        securityService.verifyPin(user, "1234");

        assertThat(user.getPinFailures()).isZero();
        assertThat(user.getPinLockedUntil()).isNull();
    }

    @Test
    void verifyPin_wrongPin_recordsFailure() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("correct" + PIN_PEPPER);
        user.setPinHash("{bcrypt}" + hash);

        assertThatThrownBy(() -> securityService.verifyPin(user, "wrong"))
                .isInstanceOf(InvalidOtpException.class);

        verify(pinLockoutService).recordFailure(1L);
        verify(monitoringService).incrementPinVerificationFailed();
    }

    @Test
    void confirmEnableLoginTwoFa_verifiedEmail_enablesAndEvicts() {
        user.setEmailVerifiedAt(Instant.now());

        securityService.confirmEnableLoginTwoFa(user, "123456");

        assertThat(user.isTwoFaLoginEnabled()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(otpService).verifyOtp(eq(user), eq("123456"),
                eq(com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose.TWOFA_ENABLE));
        verify(userRepo).save(user);
        verify(userCacheService).evict(1L);
    }

    @Test
    void requestDisableLoginTwoFa_notEnabled_throwsBadRequest() {
        user.setTwoFaLoginEnabled(false);

        assertThatThrownBy(() -> securityService.requestDisableLoginTwoFa(user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.login_2fa_not_enabled");
    }

    @Test
    void requestDisableLoginTwoFa_enabled_sendsOtp() {
        user.setTwoFaLoginEnabled(true);
        user.setEmail("sam@example.com");

        securityService.requestDisableLoginTwoFa(user);

        verify(otpService).enforceResendCooldown(1L, TWOFA_DISABLE);
        verify(otpService).persistOtp(eq(user), eq("HASH"), eq(TWOFA_DISABLE));
        verify(emailService).sendOtp(eq("sam@example.com"), eq("123456"), any());
    }

    @Test
    void verifyPinByPendingToken_correctPin_consumesToken() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        user.setPinHash("{bcrypt}" + encoder.encode("1234" + PIN_PEPPER));
        when(jwtService.extractUserIdFromPinPendingToken("tok")).thenReturn(1L);
        when(userCacheService.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.jtiFromPendingToken("tok")).thenReturn("jti-1");

        UserEntity result = securityService.verifyPinByPendingToken("tok", "1234");

        assertThat(result).isSameAs(user);
        verify(pendingTokenStore).consumeOrThrow("jti-1");
    }

    @Test
    void verifyPinByPendingToken_wrongPin_doesNotConsumeToken() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        user.setPinHash("{bcrypt}" + encoder.encode("correct" + PIN_PEPPER));
        when(jwtService.extractUserIdFromPinPendingToken("tok")).thenReturn(1L);
        when(userCacheService.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> securityService.verifyPinByPendingToken("tok", "wrong"))
                .isInstanceOf(InvalidOtpException.class);

        verify(pendingTokenStore, never()).consumeOrThrow(any());
    }

    @Test
    void confirmPinResetByPendingToken_setsPinAndConsumesToken() {
        when(jwtService.extractUserIdFromPinPendingToken("tok")).thenReturn(1L);
        when(userCacheService.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.jtiFromPendingToken("tok")).thenReturn("jti-1");

        UserEntity result = securityService.confirmPinResetByPendingToken("tok", "123456", "4321");

        assertThat(result).isSameAs(user);
        assertThat(user.isPinEnabled()).isTrue();
        verify(pendingTokenStore).consumeOrThrow("jti-1");
    }

    @Test
    void resolveRefreshUser_valid_returnsUser() {
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        when(refreshTokenService.validate("RAW")).thenReturn(1L);
        when(userCacheService.findById(1L)).thenReturn(Optional.of(user));

        assertThat(securityService.resolveRefreshUser("RAW")).isSameAs(user);
        verify(userStatusGuard).requireNotBlocked(user);
    }

    @Test
    void resolveRefreshUser_blankToken_throwsBadRequest() {
        assertThatThrownBy(() -> securityService.resolveRefreshUser("  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.invalid_refresh_token");
    }

    @Test
    void mintAccessToken_signsWithCurrentVersionAndRole() {
        user.setRole(UserRole.USER);
        when(jwtService.generateToken(1L, 100L, 0, "USER")).thenReturn("ACCESS");

        assertThat(securityService.mintAccessToken(user)).isEqualTo("ACCESS");
    }

    @Test
    void requiresLoginGate_noPinNo2fa_neverGated() {
        assertThat(securityService.requiresLoginGate(user, null)).isFalse();
    }

    @Test
    void requiresLoginGate_pinEnabled_noBearerToken_gated() {
        user.setPinEnabled(true);

        assertThat(securityService.requiresLoginGate(user, null)).isTrue();
    }

    @Test
    void requiresLoginGate_pinEnabled_provenBearerToken_notGated() {
        user.setPinEnabled(true);
        when(jwtService.isProofOfSession("STALE", 1L)).thenReturn(true);

        assertThat(securityService.requiresLoginGate(user, "STALE")).isFalse();
    }

    @Test
    void requiresLoginGate_pinEnabled_foreignBearerToken_gated() {
        user.setPinEnabled(true);
        when(jwtService.isProofOfSession("FOREIGN", 1L)).thenReturn(false);

        assertThat(securityService.requiresLoginGate(user, "FOREIGN")).isTrue();
    }

    @Test
    void requiresLoginGate_twoFaEnabled_noBearerToken_gated() {
        user.setTwoFaLoginEnabled(true);

        assertThat(securityService.requiresLoginGate(user, null)).isTrue();
    }
}
