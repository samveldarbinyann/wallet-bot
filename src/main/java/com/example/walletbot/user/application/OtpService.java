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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final EmailOtpRepository otpRepo;
    private final MailProperties mailProps;
    private final HashingService hashingService;
    private final MonitoringService monitoringService;
    private final OtpAttemptService otpAttemptService;

    public void verifyOtp(UserEntity user, String code, OtpPurpose purpose) {
        verifyOtp(user, code, purpose, null);
    }

    public void verifyOtp(UserEntity user, String code, OtpPurpose purpose, String expectedContext) {
        EmailOtpEntity otp = otpRepo
                .findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(() -> new InvalidOtpException("No active OTP found"));
        if (otp.getExpiresAt().isBefore(Instant.now()))
            throw new InvalidOtpException("OTP expired");
        if (otp.getAttempts() >= mailProps.maxAttempts())
            throw new RateLimitException("Too many failed attempts");

        if (expectedContext != null && !expectedContext.equals(otp.getContext())) {
            otpAttemptService.markUsed(otp.getId());
            log.warn("OTP context mismatch for user {} purpose {}", user.getId(), purpose);
            monitoringService.incrementOtpVerificationFailed();
            throw new InvalidOtpException("OTP context mismatch");
        }

        if (!hashingService.safeEquals(otp.getCodeHash(), hashingService.hashOtp(code))) {
            int rows = otpAttemptService.recordFailure(otp.getId(), mailProps.maxAttempts());
            log.warn("Failed OTP for user {} purpose {}", user.getId(), purpose);
            monitoringService.incrementOtpVerificationFailed();
            if (rows == 0) throw new RateLimitException("Too many failed attempts");
            throw new InvalidOtpException("Invalid code");
        }
        if (otpRepo.markUsed(otp.getId()) == 0) {
            log.warn("OTP race for user {} purpose {} — already consumed", user.getId(), purpose);
            throw new InvalidOtpException("Invalid code");
        }
        log.info("Successful OTP for user {} purpose {}", user.getId(), purpose);
    }

    public void persistOtp(UserEntity user, String codeHash, OtpPurpose purpose) {
        persistOtp(user, codeHash, purpose, null);
    }

    public void persistOtp(UserEntity user, String codeHash, OtpPurpose purpose, String context) {
        otpRepo.deleteByUserIdAndPurpose(user.getId(), purpose);
        EmailOtpEntity otp = new EmailOtpEntity();
        otp.setUser(user);
        otp.setCodeHash(codeHash);
        otp.setPurpose(purpose);
        otp.setContext(context);
        otp.setExpiresAt(Instant.now().plus(mailProps.otpTtlMinutes(), ChronoUnit.MINUTES));
        otpRepo.save(otp);
    }

    @Transactional
    public void purgeStale() {
        Instant now = Instant.now();
        otpRepo.deleteExpiredAndUsed(now, now.minus(24, ChronoUnit.HOURS));
    }

    public String generateCode() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    public void enforceResendCooldown(Long userId, OtpPurpose purpose) {
        if (isInResendCooldown(userId, purpose))
            throw new RateLimitException("Please wait before requesting a new code");
    }

    public boolean isInResendCooldown(Long userId, OtpPurpose purpose) {
        return otpRepo.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(userId, purpose)
                .map(otp -> Instant.now().isBefore(otp.getCreatedAt().plusSeconds(mailProps.resendCooldownSeconds())))
                .orElse(false);
    }
}