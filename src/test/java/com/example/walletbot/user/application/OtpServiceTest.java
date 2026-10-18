package com.example.walletbot.user.application;

import com.example.walletbot.notification.infrastructure.MailProperties;
import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.shared.exception.InvalidOtpException;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.shared.security.HashingService;
import com.example.walletbot.user.domain.EmailOtpEntity;
import com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose;
import com.example.walletbot.user.domain.EmailOtpRepository;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    EmailOtpRepository otpRepo;
    @Mock
    MailProperties mailProps;
    @Mock
    HashingService hashingService;
    @Mock
    MonitoringService monitoringService;
    @Mock
    OtpAttemptService otpAttemptService;

    @InjectMocks
    OtpService otpService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);

        lenient().when(mailProps.maxAttempts()).thenReturn(5);
        lenient().when(mailProps.otpTtlMinutes()).thenReturn(10);
        lenient().when(mailProps.resendCooldownSeconds()).thenReturn(60);
        lenient().when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void verifyOtp_validCode_marksUsedAtomically() {
        EmailOtpEntity otp = activeOtp("HASH", null);
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(otp));
        when(hashingService.hashOtp("123456")).thenReturn("HASH");
        when(hashingService.safeEquals("HASH", "HASH")).thenReturn(true);
        when(otpRepo.markUsed(10L)).thenReturn(1);

        otpService.verifyOtp(user, "123456", OtpPurpose.LOGIN);

        verify(otpRepo).markUsed(10L);
    }

    @Test
    void verifyOtp_validCodeButRaceLost_throwsInvalidOtp() {
        EmailOtpEntity otp = activeOtp("HASH", null);
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(otp));
        when(hashingService.hashOtp("123456")).thenReturn("HASH");
        when(hashingService.safeEquals("HASH", "HASH")).thenReturn(true);
        when(otpRepo.markUsed(10L)).thenReturn(0);

        assertThatThrownBy(() -> otpService.verifyOtp(user, "123456", OtpPurpose.LOGIN))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void verifyOtp_noActiveOtp_throwsInvalidOtp() {
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyOtp(user, "123456", OtpPurpose.LOGIN))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void verifyOtp_expired_throwsInvalidOtp() {
        EmailOtpEntity otp = activeOtp("HASH", null);
        otp.setExpiresAt(Instant.now().minusSeconds(1));
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verifyOtp(user, "123456", OtpPurpose.LOGIN))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyOtp_maxAttemptsReached_throwsRateLimit() {
        EmailOtpEntity otp = activeOtp("HASH", null);
        otp.setAttempts(5);
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verifyOtp(user, "123456", OtpPurpose.LOGIN))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void verifyOtp_contextMismatch_marksUsedAndThrows() {
        EmailOtpEntity otp = activeOtp("HASH", "expected-ctx");
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_BIND))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verifyOtp(user, "123456", OtpPurpose.EMAIL_BIND, "wrong-ctx"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("context");

        verify(otpAttemptService).markUsed(10L);
        verify(monitoringService).incrementOtpVerificationFailed();
    }

    @Test
    void verifyOtp_wrongCode_incrementsAttemptsAndThrows() {
        EmailOtpEntity otp = activeOtp("CORRECT_HASH", null);
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(otp));
        when(hashingService.hashOtp("wrong")).thenReturn("WRONG_HASH");
        when(hashingService.safeEquals("CORRECT_HASH", "WRONG_HASH")).thenReturn(false);
        when(otpAttemptService.recordFailure(any(), eq(5))).thenReturn(1);

        assertThatThrownBy(() -> otpService.verifyOtp(user, "wrong", OtpPurpose.LOGIN))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid");

        verify(monitoringService).incrementOtpVerificationFailed();
    }

    @Test
    void verifyOtp_wrongCodeAndLocked_throwsRateLimit() {
        EmailOtpEntity otp = activeOtp("CORRECT_HASH", null);
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(otp));
        when(hashingService.hashOtp("wrong")).thenReturn("WRONG_HASH");
        when(hashingService.safeEquals("CORRECT_HASH", "WRONG_HASH")).thenReturn(false);
        when(otpAttemptService.recordFailure(any(), eq(5))).thenReturn(0);

        assertThatThrownBy(() -> otpService.verifyOtp(user, "wrong", OtpPurpose.LOGIN))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void persistOtp_deletesOldAndSavesNew() {
        otpService.persistOtp(user, "HASH", OtpPurpose.PIN_RESET);

        verify(otpRepo).deleteByUserIdAndPurpose(1L, OtpPurpose.PIN_RESET);
        verify(otpRepo).save(any(EmailOtpEntity.class));
    }

    @Test
    void persistOtp_withContext_setsContext() {
        otpService.persistOtp(user, "HASH", OtpPurpose.EMAIL_BIND, "ctx-value");

        verify(otpRepo).save(argThat(e -> "ctx-value".equals(e.getContext())));
    }

    @Test
    void generateCode_isSixDigits() {
        for (int i = 0; i < 20; i++) {
            String code = otpService.generateCode();
            assertThat(code).matches("\\d{6}");
        }
    }

    @Test
    void enforceResendCooldown_withinCooldown_throwsRateLimit() {
        EmailOtpEntity otp = activeOtp("HASH", null);
        otp.setCreatedAt(Instant.now().minusSeconds(30));
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.enforceResendCooldown(1L, OtpPurpose.LOGIN))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void enforceResendCooldown_outsideCooldown_noException() {
        EmailOtpEntity otp = activeOtp("HASH", null);
        otp.setCreatedAt(Instant.now().minusSeconds(120));
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(otp));

        otpService.enforceResendCooldown(1L, OtpPurpose.LOGIN);
    }

    @Test
    void enforceResendCooldown_noOtp_noException() {
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.empty());

        otpService.enforceResendCooldown(1L, OtpPurpose.LOGIN);
    }

    @Test
    void isInResendCooldown_withinCooldown_returnsTrue() {
        EmailOtpEntity otp = activeOtp("HASH", null);
        otp.setCreatedAt(Instant.now().minusSeconds(10));
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(otp));

        assertThat(otpService.isInResendCooldown(1L, OtpPurpose.LOGIN)).isTrue();
    }

    @Test
    void isInResendCooldown_noPriorOtp_returnsFalse() {
        when(otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(1L, OtpPurpose.LOGIN))
                .thenReturn(Optional.empty());

        assertThat(otpService.isInResendCooldown(1L, OtpPurpose.LOGIN)).isFalse();
    }

    private EmailOtpEntity activeOtp(String codeHash, String context) {
        EmailOtpEntity otp = new EmailOtpEntity();
        otp.setId(10L);
        otp.setCodeHash(codeHash);
        otp.setContext(context);
        otp.setExpiresAt(Instant.now().plusSeconds(600));
        otp.setAttempts(0);
        otp.setUsed(false);
        otp.setCreatedAt(Instant.now());
        return otp;
    }
}
