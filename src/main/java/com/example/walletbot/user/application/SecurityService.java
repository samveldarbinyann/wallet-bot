package com.example.walletbot.user.application;

import com.example.walletbot.notification.application.EmailService;
import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.InvalidOtpException;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.shared.security.HashingService;
import com.example.walletbot.shared.security.JwtService;
import com.example.walletbot.shared.security.PendingTokenStore;
import com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose;
import com.example.walletbot.user.domain.EmailOtpRepository;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import com.example.walletbot.user.infrastructure.UserCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose.*;

@Slf4j
@Service
@Transactional
public class SecurityService {

    private final EmailOtpRepository otpRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final UserCacheService userCacheService;
    private final MonitoringService monitoringService;
    private final HashingService hashingService;
    private final OtpService otpService;
    private final UserStatusGuard userStatusGuard;
    private final PinLockoutService pinLockoutService;
    private final PendingTokenStore pendingTokenStore;
    private final RefreshTokenService refreshTokenService;

    @Value("${security.pin-pepper}")
    private String pinPepper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public SecurityService(EmailOtpRepository otpRepo,
                           UserRepository userRepo,
                           EmailService emailService,
                           JwtService jwtService,
                           UserCacheService userCacheService,
                           MonitoringService monitoringService,
                           HashingService hashingService,
                           OtpService otpService,
                           UserStatusGuard userStatusGuard,
                           PinLockoutService pinLockoutService,
                           PendingTokenStore pendingTokenStore,
                           RefreshTokenService refreshTokenService) {
        this.otpRepo = otpRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.userCacheService = userCacheService;
        this.monitoringService = monitoringService;
        this.userStatusGuard = userStatusGuard;
        this.hashingService = hashingService;
        this.otpService = otpService;
        this.pinLockoutService = pinLockoutService;
        this.pendingTokenStore = pendingTokenStore;
        this.refreshTokenService = refreshTokenService;
    }

    private void bumpTokenVersion(UserEntity user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenService.revokeAll(user.getId());
    }

    private void persistSecurityChange(UserEntity user) {
        bumpTokenVersion(user);
        userRepo.save(user);
        userCacheService.evict(user.getId());
    }

    private void issueOtp(UserEntity user, OtpPurpose purpose) {
        otpService.enforceResendCooldown(user.getId(), purpose);
        String code = otpService.generateCode();
        otpService.persistOtp(user, hashingService.hashOtp(code), purpose);
        emailService.sendOtp(user.getEmail(), code, user.getLang());
    }

    public void logout(UserEntity user) {
        persistSecurityChange(user);
    }

    @Transactional(readOnly = true)
    public UserEntity resolveRefreshUser(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadRequestException("error.invalid_refresh_token");
        }
        Long userId = refreshTokenService.validate(rawRefreshToken);
        UserEntity user = userCacheService.findById(userId)
                .orElseThrow(() -> new BadRequestException("error.user_not_found"));
        userStatusGuard.requireNotBlocked(user);
        return user;
    }

    @Transactional(readOnly = true)
    public String mintAccessToken(UserEntity user) {
        return jwtService.generateToken(
                user.getId(), user.getTelegramId(), user.getTokenVersion(), user.getRole().name());
    }

    @Transactional(readOnly = true)
    public boolean requiresLoginGate(UserEntity user, String bearerToken) {
        boolean proven = bearerToken != null && jwtService.isProofOfSession(bearerToken, user.getId());
        return !proven && (user.isPinEnabled() || user.isTwoFaLoginEnabled());
    }

    public void initiateEmailBinding(UserEntity user, String email) {
        String normalized = normalize(email);
        validateEmailFormat(normalized);
        otpService.enforceResendCooldown(user.getId(), EMAIL_BIND);
        String code = otpService.generateCode();
        otpService.persistOtp(user, hashingService.hashOtp(code), EMAIL_BIND, normalized);
        user.setPendingEmail(normalized);
        userRepo.save(user);
        emailService.sendOtp(normalized, code, user.getLang());
    }

    public void confirmEmailBinding(UserEntity user, String code) {
        if (user.getPendingEmail() == null) {
            throw new BadRequestException("error.no_pending_email_binding");
        }
        otpService.verifyOtp(user, code, EMAIL_BIND, user.getPendingEmail());
        user.setEmail(user.getPendingEmail());
        user.setEmailHash(hashingService.hashEmail(user.getPendingEmail()));
        user.setPendingEmail(null);
        user.setEmailVerifiedAt(Instant.now());
        persistSecurityChange(user);
        otpRepo.deleteByUserIdAndPurposeNot(user.getId(), EMAIL_BIND);
    }

    public void removeEmail(UserEntity user) {
        if (user.isPinEnabled() || user.isTwoFaLoginEnabled())
            throw new BadRequestException("error.disable_pin_2fa_before_email_unlink");
        clearEmailAndPersist(user);
    }

    private void clearEmailAndPersist(UserEntity user) {
        otpRepo.deleteByUserId(user.getId());
        user.setEmail(null);
        user.setEmailHash(null);
        user.setEmailVerifiedAt(null);
        persistSecurityChange(user);
    }

    public void requestEmailUnlink(UserEntity user) {
        if (user.getEmail() == null || user.getEmailVerifiedAt() == null)
            throw new BadRequestException("error.no_verified_email");
        if (user.isPinEnabled() || user.isTwoFaLoginEnabled())
            throw new BadRequestException("error.disable_pin_2fa_before_email_unlink");
        issueOtp(user, EMAIL_UNLINK);
    }

    public void confirmEmailUnlink(UserEntity user, String code) {
        otpService.verifyOtp(user, code, EMAIL_UNLINK);
        clearEmailAndPersist(user);
    }

    public void requestPinSetup(UserEntity user) {
        if (user.getEmailVerifiedAt() == null)
            throw new BadRequestException("error.verify_email_first");
        if (user.isPinEnabled())
            throw new BadRequestException("error.pin_already_set");
        issueOtp(user, PIN_SETUP);
    }

    public void confirmPinSetup(UserEntity user, String code, String pin) {
        if (user.getEmailVerifiedAt() == null)
            throw new BadRequestException("error.verify_email_first");
        if (user.isPinEnabled())
            throw new BadRequestException("error.pin_already_set");
        otpService.verifyOtp(user, code, PIN_SETUP);
        user.setPinHash("{bcrypt}" + passwordEncoder.encode(pin + pinPepper));
        user.setPinEnabled(true);
        persistSecurityChange(user);
        otpRepo.deleteByUserIdAndPurposeNot(user.getId(), PIN_SETUP);
    }

    public void changePin(UserEntity user, String currentPin, String newPin) {
        checkPinOrFail(user, currentPin);
        user.setPinHash("{bcrypt}" + passwordEncoder.encode(newPin + pinPepper));
        persistSecurityChange(user);
    }

    public void disablePin(UserEntity user, String currentPin) {
        checkPinOrFail(user, currentPin);
        user.setPinHash(null);
        user.setPinEnabled(false);
        resetPinLockout(user);
        persistSecurityChange(user);
    }

    public void verifyPin(UserEntity user, String pin) {
        checkPinOrFail(user, pin);
    }

    public UserEntity verifyPinByPendingToken(String pinPendingToken, String pin) {
        UserEntity user = resolveUserFromPinPendingToken(pinPendingToken);
        verifyPin(user, pin);
        pendingTokenStore.consumeOrThrow(jwtService.jtiFromPendingToken(pinPendingToken));
        return user;
    }

    private void checkPinOrFail(UserEntity user, String pin) {
        if (user.getPinLockedUntil() != null && Instant.now().isBefore(user.getPinLockedUntil())) {
            long retryAfter = Math.max(1, user.getPinLockedUntil().getEpochSecond() - Instant.now().getEpochSecond());
            throw new RateLimitException("Too many failed attempts. Try again later.", retryAfter);
        }

        String hash = user.getPinHash();
        if (hash == null || !hash.startsWith("{bcrypt}")) {
            recordPinFailure(user);
            throw new InvalidOtpException("Wrong PIN");
        }

        boolean match = passwordEncoder.matches(pin + pinPepper, hash.substring("{bcrypt}".length()));

        if (!match) {
            log.warn("Failed PIN verification for user {}", user.getId());
            monitoringService.incrementPinVerificationFailed();
            recordPinFailure(user);
            throw new InvalidOtpException("Wrong PIN");
        }
        log.info("Successful PIN verification for user {}", user.getId());
        resetPinLockout(user);
    }

    private void recordPinFailure(UserEntity user) {
        pinLockoutService.recordFailure(user.getId());
    }

    private void resetPinLockout(UserEntity user) {
        if (user.getPinFailures() > 0 || user.getPinLockedUntil() != null) {
            pinLockoutService.resetLockout(user.getId());
            user.setPinFailures(0);
            user.setPinLockedUntil(null);
        }
    }

    public UserEntity resolveUserFromPinPendingToken(String token) {
        Long userId = jwtService.extractUserIdFromPinPendingToken(token);
        return userCacheService.findById(userId)
                .orElseThrow(() -> new BadRequestException("error.user_not_found"));
    }

    public void requestPinReset(UserEntity user) {
        if (user.getEmail() == null || user.getEmailVerifiedAt() == null)
            throw new BadRequestException("error.no_verified_email");
        issueOtp(user, PIN_RESET);
    }

    public void confirmPinReset(UserEntity user, String code, String newPin) {
        boolean disabling = newPin == null || newPin.isBlank();
        otpService.verifyOtp(user, code, PIN_RESET);
        if (!disabling) {
            user.setPinHash("{bcrypt}" + passwordEncoder.encode(newPin + pinPepper));
            user.setPinEnabled(true);
        } else {
            user.setPinHash(null);
            user.setPinEnabled(false);
        }
        user.setPinFailures(0);
        user.setPinLockedUntil(null);
        persistSecurityChange(user);
        otpRepo.deleteByUserIdAndPurposeNot(user.getId(), PIN_RESET);
    }

    public UserEntity confirmPinResetByPendingToken(String pinPendingToken, String code, String newPin) {
        UserEntity user = resolveUserFromPinPendingToken(pinPendingToken);
        confirmPinReset(user, code, newPin);
        pendingTokenStore.consumeOrThrow(jwtService.jtiFromPendingToken(pinPendingToken));
        return user;
    }

    public void requestEnableLoginTwoFa(UserEntity user) {
        if (user.isTwoFaLoginEnabled())
            throw new BadRequestException("error.login_2fa_already_enabled");
        if (user.getEmailVerifiedAt() == null)
            throw new BadRequestException("error.verify_email_first");
        issueOtp(user, TWOFA_ENABLE);
    }

    public void confirmEnableLoginTwoFa(UserEntity user, String code) {
        if (user.isTwoFaLoginEnabled())
            throw new BadRequestException("error.login_2fa_already_enabled");
        if (user.getEmailVerifiedAt() == null)
            throw new BadRequestException("error.verify_email_first");
        otpService.verifyOtp(user, code, TWOFA_ENABLE);
        user.setTwoFaLoginEnabled(true);
        persistSecurityChange(user);
        otpRepo.deleteByUserIdAndPurposeNot(user.getId(), TWOFA_ENABLE);
    }

    public void requestDisableLoginTwoFa(UserEntity user) {
        if (!user.isTwoFaLoginEnabled())
            throw new BadRequestException("error.login_2fa_not_enabled");
        issueOtp(user, TWOFA_DISABLE);
    }

    public void confirmDisableLoginTwoFa(UserEntity user, String code) {
        otpService.verifyOtp(user, code, TWOFA_DISABLE);
        user.setTwoFaLoginEnabled(false);
        persistSecurityChange(user);
        otpRepo.deleteByUserIdAndPurposeNot(user.getId(), TWOFA_DISABLE);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private void validateEmailFormat(String email) {
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new BadRequestException("error.invalid_email_format");
    }
}
