package com.example.walletbot.user.api;

import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.platform.web.security.AuthRateLimiter;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.platform.web.security.TelegramAuthService;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.user.api.dto.*;
import com.example.walletbot.user.application.*;
import com.example.walletbot.user.domain.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class AuthController {

    private final TelegramAuthService telegramAuthService;
    private final UserService userService;
    private final AuthRateLimiter authRateLimiter;
    private final TwoFaService twoFaService;
    private final MonitoringService monitoringService;
    private final UserMapper userMapper;
    private final UserRestMapper userRestMapper;
    private final SecurityService securityService;
    private final RefreshCookie refreshCookie;
    private final UserStatusGuard userStatusGuard;

    @PostMapping("/auth")
    public ResponseEntity<Object> authenticate(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {

        if (!authRateLimiter.isAllowed(httpServletRequest)) {
            monitoringService.incrementAuthFailed();
            throw new RateLimitException("Too many requests");
        }

        TelegramAuthService.TelegramAuthResult authResult = telegramAuthService.validate(request.initData());
        UserEntity user = userService.getOrCreateUser(
                authResult.telegramId(), authResult.username(), authResult.firstName(), authResult.lastName(),
                authResult.languageCode());
        userStatusGuard.requireNotBlocked(user);
        userService.updateLastLoginAt(user);

        return respondToAuthSession(user, httpServletResponse);
    }

    @PostMapping("/auth/2fa/resend")
    public ResponseEntity<Void> resendTwoFaCode(@Valid @RequestBody ResendOtpRequest request) {
        UserEntity user = twoFaService.resolveUserFromPendingToken(request.pendingToken());
        twoFaService.sendLoginOtp(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        if (!authRateLimiter.isAllowed(request)) {
            throw new RateLimitException("Too many requests");
        }
        UserEntity user = securityService.resolveRefreshUser(refreshCookie.read(request));

        if (securityService.requiresLoginGate(user, bearerToken(request))) {
            return respondToAuthSession(user, response);
        }
        return ResponseEntity.ok(new RefreshResponse(securityService.mintAccessToken(user)));
    }

    private ResponseEntity<Object> respondToAuthSession(UserEntity user, HttpServletResponse response) {
        return switch (twoFaService.resolveAuthSession(user)) {
            case TwoFaService.AuthSessionResult.PinRequired r ->
                    ResponseEntity.status(202).body(new PinPendingResponse(r.pinPendingToken(), r.maskedEmail()));
            case TwoFaService.AuthSessionResult.TokenIssued r -> {
                refreshCookie.set(response, r.session().refreshToken());
                yield ResponseEntity.ok(new AuthResponse(
                        r.session().token(),
                        userRestMapper.toRestDto(userMapper.toDto(user))));
            }
        };
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@CurrentUser UserEntity user, HttpServletResponse response) {
        securityService.logout(user);
        refreshCookie.clear(response);
        return ResponseEntity.noContent().build();
    }
}
