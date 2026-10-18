package com.example.walletbot.user.api;

import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.platform.web.security.OtpRateLimiter;
import com.example.walletbot.platform.web.security.OtpVerifyRateLimiter;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.user.api.dto.*;
import com.example.walletbot.user.application.SecurityService;
import com.example.walletbot.user.application.TwoFaService;
import com.example.walletbot.user.application.UserMapper;
import com.example.walletbot.user.domain.UserEntity;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/miniapp/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SecurityService securityService;
    private final TwoFaService twoFaService;
    private final OtpRateLimiter otpRateLimiter;
    private final OtpVerifyRateLimiter otpVerifyRateLimiter;
    private final UserMapper userMapper;
    private final UserRestMapper userRestMapper;
    private final RefreshCookie refreshCookie;

    @PostMapping("/email/start")
    public ResponseEntity<Void> emailStart(
            @CurrentUser UserEntity user,
            @Valid @RequestBody SetupEmailRequest req) {
        requireOtpQuota(user.getId());
        securityService.initiateEmailBinding(user, req.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/confirm")
    public ResponseEntity<UserResponse> emailConfirm(
            @CurrentUser UserEntity user,
            @Valid @RequestBody VerifyOtpRequest req) {
        securityService.confirmEmailBinding(user, req.code());
        return ResponseEntity.ok(toRestDto(user));
    }

    @DeleteMapping("/email")
    public ResponseEntity<Void> emailRemove(@CurrentUser UserEntity user) {
        securityService.removeEmail(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/unlink/request")
    public ResponseEntity<Void> emailUnlinkRequest(@CurrentUser UserEntity user) {
        requireOtpQuota(user.getId());
        securityService.requestEmailUnlink(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/unlink/confirm")
    public ResponseEntity<Void> emailUnlinkConfirm(
            @CurrentUser UserEntity user,
            @Valid @RequestBody VerifyOtpRequest req) {
        securityService.confirmEmailUnlink(user, req.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pin/setup/request")
    public ResponseEntity<Void> pinSetupRequest(@CurrentUser UserEntity user) {
        requireOtpQuota(user.getId());
        securityService.requestPinSetup(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pin/setup/confirm")
    public ResponseEntity<UserResponse> pinSetupConfirm(
            @CurrentUser UserEntity user,
            @Valid @RequestBody ConfirmPinSetupRequest req) {
        securityService.confirmPinSetup(user, req.otpCode(), req.pin());
        return ResponseEntity.ok(toRestDto(user));
    }

    @DeleteMapping("/pin")
    public ResponseEntity<Void> pinDisable(
            @CurrentUser UserEntity user,
            @Valid @RequestBody DisablePinRequest req) {
        securityService.disablePin(user, req.currentPin());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pin/change")
    public ResponseEntity<UserResponse> pinChange(
            @CurrentUser UserEntity user,
            @Valid @RequestBody ChangePinRequest req) {
        securityService.changePin(user, req.currentPin(), req.newPin());
        return ResponseEntity.ok(toRestDto(user));
    }

    @PostMapping("/pin/reset/request")
    public ResponseEntity<Void> pinResetRequest(@CurrentUser UserEntity user) {
        requireOtpQuota(user.getId());
        securityService.requestPinReset(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pin/reset/confirm")
    public ResponseEntity<UserResponse> pinResetConfirm(
            @CurrentUser UserEntity user,
            @Valid @RequestBody ConfirmPinResetRequest req) {
        securityService.confirmPinReset(user, req.code(), req.newPin());
        return ResponseEntity.ok(toRestDto(user));
    }

    @PostMapping("/pin/reset/request-token")
    public ResponseEntity<Void> pinResetRequestByToken(@Valid @RequestBody PinResetTokenRequest req) {
        UserEntity user = securityService.resolveUserFromPinPendingToken(req.pinPendingToken());
        requireOtpQuota(user.getId());
        securityService.requestPinReset(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pin/reset/confirm-token")
    public ResponseEntity<AuthResponse> pinResetConfirmByToken(@Valid @RequestBody ConfirmPinResetTokenRequest req,
                                                               HttpServletResponse response) {
        UserEntity gateUser = securityService.resolveUserFromPinPendingToken(req.pinPendingToken());
        if (!otpVerifyRateLimiter.isAllowed(gateUser.getId()))
            throw new RateLimitException("Too many attempts");
        UserEntity user = securityService.confirmPinResetByPendingToken(
                req.pinPendingToken(), req.code(), req.newPin());
        return issueSessionResponse(user, response);
    }

    @PostMapping("/pin/verify")
    public ResponseEntity<AuthResponse> pinVerify(@Valid @RequestBody PinVerifyRequest req,
                                                   HttpServletResponse response) {
        UserEntity user = securityService.verifyPinByPendingToken(req.pinPendingToken(), req.pin());
        return issueSessionResponse(user, response);
    }

    private ResponseEntity<AuthResponse> issueSessionResponse(UserEntity user, HttpServletResponse response) {
        TwoFaService.IssuedSession session = twoFaService.issueTokenOrGate(user);
        refreshCookie.set(response, session.refreshToken());
        return ResponseEntity.ok(new AuthResponse(session.token(), toRestDto(user)));
    }

    @PostMapping("/2fa/login/enable/request")
    public ResponseEntity<Void> enableLoginTwoFaRequest(@CurrentUser UserEntity user) {
        requireOtpQuota(user.getId());
        securityService.requestEnableLoginTwoFa(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/2fa/login/enable/confirm")
    public ResponseEntity<UserResponse> enableLoginTwoFaConfirm(
            @CurrentUser UserEntity user,
            @Valid @RequestBody VerifyOtpRequest req) {
        securityService.confirmEnableLoginTwoFa(user, req.code());
        return ResponseEntity.ok(toRestDto(user));
    }

    @PostMapping("/2fa/login/disable/request")
    public ResponseEntity<Void> disableLoginTwoFaRequest(@CurrentUser UserEntity user) {
        requireOtpQuota(user.getId());
        securityService.requestDisableLoginTwoFa(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/2fa/login/disable/confirm")
    public ResponseEntity<UserResponse> disableLoginTwoFaConfirm(
            @CurrentUser UserEntity user,
            @Valid @RequestBody VerifyOtpRequest req) {
        securityService.confirmDisableLoginTwoFa(user, req.code());
        return ResponseEntity.ok(toRestDto(user));
    }

    private void requireOtpQuota(Long userId) {
        if (!otpRateLimiter.isAllowed(userId))
            throw new RateLimitException("Too many requests");
    }

    private UserResponse toRestDto(UserEntity user) {
        return userRestMapper.toRestDto(userMapper.toDto(user));
    }
}
