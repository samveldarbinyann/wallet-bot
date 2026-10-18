package com.example.walletbot.user.application;

import com.example.walletbot.notification.application.EmailService;
import com.example.walletbot.platform.web.security.OtpRateLimiter;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.shared.exception.TwoFaRequiredException;
import com.example.walletbot.shared.security.HashingService;
import com.example.walletbot.shared.security.JwtService;
import com.example.walletbot.shared.security.PendingTokenStore;
import com.example.walletbot.shared.util.EmailMasker;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.infrastructure.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose.LOGIN;

@Service
@Transactional(noRollbackFor = TwoFaRequiredException.class)
@RequiredArgsConstructor
public class TwoFaService {

    private final EmailService emailService;
    private final JwtService jwtService;
    private final UserCacheService userCacheService;
    private final HashingService hashingService;
    private final OtpService otpService;
    private final OtpRateLimiter otpRateLimiter;
    private final PendingTokenStore pendingTokenStore;
    private final RefreshTokenService refreshTokenService;

    public void sendLoginOtp(UserEntity user) {
        if (otpService.isInResendCooldown(user.getId(), LOGIN)) return;
        if (!otpRateLimiter.isAllowed(user.getId()))
            throw new RateLimitException("Too many login code requests. Please try again later.");
        String code = otpService.generateCode();
        otpService.persistOtp(user, hashingService.hashOtp(code), LOGIN);
        emailService.sendOtp(user.getEmail(), code, user.getLang());
    }

    public IssuedSession verifyLoginOtp(UserEntity user, String pendingToken, String code) {
        jwtService.validatePendingToken(pendingToken, user.getId());
        otpService.verifyOtp(user, code, LOGIN);
        pendingTokenStore.consumeOrThrow(jwtService.jtiFromPendingToken(pendingToken));
        return mintSession(user);
    }

    public UserEntity resolveUserFromPendingToken(String pendingToken) {
        Long userId = jwtService.extractUserIdFromPendingToken(pendingToken);
        return userCacheService.findById(userId)
                .orElseThrow(() -> new BadRequestException("error.user_not_found"));
    }

    public IssuedSession issueTokenOrGate(UserEntity user) {
        if (user.isTwoFaLoginEnabled()) {
            String pendingToken = jwtService.generatePendingToken(user.getId(), user.getTelegramId());
            sendLoginOtp(user);
            throw new TwoFaRequiredException(pendingToken, EmailMasker.mask(user.getEmail()));
        }
        return mintSession(user);
    }

    public AuthSessionResult resolveAuthSession(UserEntity user) {
        if (user.isPinEnabled() && !user.isTwoFaLoginEnabled()) {
            String pinPendingToken = jwtService.generatePinPendingToken(user.getId(), user.getTelegramId());
            String maskedEmail = user.getEmail() != null ? EmailMasker.mask(user.getEmail()) : null;
            return new AuthSessionResult.PinRequired(pinPendingToken, maskedEmail);
        }
        return new AuthSessionResult.TokenIssued(issueTokenOrGate(user));
    }

    private IssuedSession mintSession(UserEntity user) {
        String access = jwtService.generateToken(user.getId(), user.getTelegramId(), user.getTokenVersion(), user.getRole().name());
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user.getId());
        return new IssuedSession(access, refresh.rawToken());
    }

    public sealed interface AuthSessionResult permits AuthSessionResult.TokenIssued, AuthSessionResult.PinRequired {
        record TokenIssued(IssuedSession session) implements AuthSessionResult {
        }

        record PinRequired(String pinPendingToken, String maskedEmail) implements AuthSessionResult {
        }
    }

    public record IssuedSession(String token, String refreshToken) {
    }
}
