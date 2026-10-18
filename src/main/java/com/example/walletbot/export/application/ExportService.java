package com.example.walletbot.export.application;

import com.example.walletbot.export.domain.ExportConfirmDto;
import com.example.walletbot.export.domain.ExportRequestDto;
import com.example.walletbot.export.domain.ExportRevealDto;
import com.example.walletbot.export.domain.ExportWalletInfo;
import com.example.walletbot.export.infrastructure.ExportIntentStore;
import com.example.walletbot.export.infrastructure.ExportIntentStore.State;
import com.example.walletbot.export.infrastructure.ExportSessionStore;
import com.example.walletbot.export.infrastructure.ExportSessionStore.RevealOutcome;
import com.example.walletbot.export.infrastructure.ExportSessionStore.RevealResult;
import com.example.walletbot.notification.application.EmailService;
import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.platform.web.security.OtpVerifyRateLimiter;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.NotFoundException;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.shared.security.HashingService;
import com.example.walletbot.shared.security.JwtService;
import com.example.walletbot.user.application.OtpService;
import com.example.walletbot.user.application.UserStatusGuard;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose.EXPORT_KEYS;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExportService {

    private final JwtService jwtService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final WalletRepository walletRepo;
    private final ExportIntentStore intentStore;
    private final ExportSessionStore sessionStore;
    private final ExportRateLimiter rateLimiter;
    private final OtpVerifyRateLimiter otpVerifyRateLimiter;
    private final MonitoringService monitoring;
    private final HashingService hashingService;
    private final UserStatusGuard userStatusGuard;

    public ExportRequestDto request(UserEntity user) {
        userStatusGuard.requireCanTransact(user);
        if (user.getEmail() == null || user.getEmailVerifiedAt() == null)
            throw new BadRequestException("error.verify_email_first");
        otpService.enforceResendCooldown(user.getId(), EXPORT_KEYS);
        if (!rateLimiter.isAllowed(user.getId())) {
            monitoring.incrementExportFailedRateLimit();
            log.warn("Export request rate-limited for user {}", user.getId());
            throw new RateLimitException("Too many export requests. Try again later.");
        }

        String code = otpService.generateCode();
        otpService.persistOtp(user, hashingService.hashOtp(code), EXPORT_KEYS);
        emailService.sendExportOtp(user.getEmail(), code, user.getLang());

        String jti = UUID.randomUUID().toString();
        String exportPendingToken = jwtService.generateExportPendingToken(user.getId(), jti);
        intentStore.issue(jti);

        monitoring.incrementExportRequested();
        log.info("Export requested for user {}", user.getId());

        return new ExportRequestDto(exportPendingToken);
    }

    private static void requireTokenUser(Claims claims, UserEntity user) {
        if (!Long.valueOf(claims.getSubject()).equals(user.getId()))
            throw new BadRequestException("error.token_user_mismatch");
    }

    public ExportConfirmDto confirm(UserEntity user, String exportPendingToken,
                                    String otpCode) {
        Claims claims;
        try {
            claims = jwtService.parseExportPending(exportPendingToken);
        } catch (BadRequestException | JwtException e) {
            monitoring.incrementExportFailedTokenInvalid();
            log.warn("Export confirm: invalid pending token for user {} ({}: {})",
                    user.getId(), e.getClass().getSimpleName(), e.getMessage());
            throw new BadRequestException("error.export_token_invalid");
        }

        requireTokenUser(claims, user);

        String jti = claims.get("jti", String.class);
        if (intentStore.peek(jti) != State.ISSUED) {
            monitoring.incrementExportFailedBurned();
            throw new BadRequestException("error.export_token_used_or_expired");
        }

        if (!otpVerifyRateLimiter.isAllowed(user.getId())) {
            monitoring.incrementExportFailedRateLimit();
            throw new RateLimitException("Too many export attempts. Try again later.");
        }

        try {
            otpService.verifyOtp(user, otpCode, EXPORT_KEYS);
        } catch (Exception e) {
            monitoring.incrementExportFailedOtp();
            throw e;
        }

        State state = intentStore.consume(jti);
        if (state != State.ISSUED) {
            monitoring.incrementExportFailedBurned();
            throw new BadRequestException("error.export_token_used_or_expired");
        }

        List<ExportWalletInfo> walletDtos = walletRepo.findByUserEntityId(user.getId()).stream()
                .filter(w -> ExportNetworkCoverage.isExportable(w.getNetwork()))
                .map(w -> new ExportWalletInfo(
                        w.getId(),
                        w.getNetwork().name(),
                        w.getAddress(),
                        ExportNetworkCoverage.coversFor(w.getNetwork())
                ))
                .toList();

        String sid = UUID.randomUUID().toString();
        String sessionToken = jwtService.generateExportSessionToken(user.getId(), user.getTokenVersion(), sid);
        sessionStore.open(sid, user.getId(), user.getTokenVersion());

        monitoring.incrementExportConfirmed();
        log.info("Export confirmed for user {}", user.getId());

        return new ExportConfirmDto(sessionToken, walletDtos);
    }

    @Transactional(readOnly = true)
    public ExportRevealDto reveal(UserEntity user, String exportSessionToken,
                                  long walletId) {
        userStatusGuard.requireCanTransact(user);

        Claims claims;
        try {
            claims = jwtService.parseExportSession(exportSessionToken);
        } catch (BadRequestException | JwtException e) {
            monitoring.incrementExportFailedTokenInvalid();
            log.warn("Export reveal: invalid session token for user {} ({}: {})",
                    user.getId(), e.getClass().getSimpleName(), e.getMessage());
            throw new BadRequestException("error.export_session_token_invalid_or_expired");
        }

        requireTokenUser(claims, user);

        String sid = claims.get("sid", String.class);
        Integer claimedTv = claims.get("tv", Integer.class);
        if (claimedTv == null || !claimedTv.equals(user.getTokenVersion())) {
            sessionStore.burn(sid);
            throw new BadRequestException("error.export_session_invalidated");
        }

        RevealOutcome outcome = sessionStore.tryReveal(sid, user.getId(), user.getTokenVersion());
        if (outcome.result() != RevealResult.OK) {
            monitoring.incrementExportFailedBurned();
            if (outcome.result() == RevealResult.WRONG_USER)
                log.warn("Export reveal WRONG_USER for user {} sid {}", user.getId(), sid);
            throw new BadRequestException("error.export_session_expired");
        }

        WalletEntity wallet = walletRepo.findByIdAndUserEntityId(walletId, user.getId())
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        if (!ExportNetworkCoverage.isExportable(wallet.getNetwork()))
            throw new BadRequestException("error.export_network_not_exportable");

        String privateKey = wallet.getPrivateKey();
        String format = ExportNetworkCoverage.formatFor(wallet.getNetwork());
        Instant expiresAt = claims.getExpiration().toInstant();

        monitoring.incrementExportRevealedFor(wallet.getNetwork().name());
        log.info("Export reveal for user {} wallet {} network {}", user.getId(), walletId, wallet.getNetwork());

        return new ExportRevealDto(
                wallet.getId(),
                wallet.getNetwork().name(),
                wallet.getAddress(),
                privateKey,
                format,
                expiresAt
        );
    }

    public void close(UserEntity user, String exportSessionToken) {
        try {
            Claims claims = jwtService.parseExportSession(exportSessionToken);
            requireTokenUser(claims, user);
            sessionStore.burn(claims.get("sid", String.class));
            log.info("Export session closed for user {}", user.getId());
        } catch (BadRequestException | JwtException ignored) {
        } catch (Exception e) {
            log.warn("Export close failed unexpectedly for user {} ({}: {})",
                    user.getId(), e.getClass().getSimpleName(), e.getMessage());
        }
    }
}