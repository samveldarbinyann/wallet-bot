package com.example.walletbot.user.api;

import com.example.walletbot.platform.web.security.OtpVerifyRateLimiter;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.user.api.dto.TwoFaLoginRequest;
import com.example.walletbot.user.api.dto.TwoFaLoginResponse;
import com.example.walletbot.user.application.TwoFaService;
import com.example.walletbot.user.application.UserMapper;
import com.example.walletbot.user.domain.UserEntity;
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
public class TwoFaController {

    private final TwoFaService twoFaService;
    private final UserMapper userMapper;
    private final UserRestMapper userRestMapper;
    private final RefreshCookie refreshCookie;
    private final OtpVerifyRateLimiter otpVerifyRateLimiter;

    @PostMapping("/auth/2fa")
    public ResponseEntity<TwoFaLoginResponse> verifyLogin(
            @Valid @RequestBody TwoFaLoginRequest req,
            HttpServletResponse response) {
        UserEntity user = twoFaService.resolveUserFromPendingToken(req.pendingToken());
        if (!otpVerifyRateLimiter.isAllowed(user.getId()))
            throw new RateLimitException("Too many attempts");
        TwoFaService.IssuedSession session = twoFaService.verifyLoginOtp(user, req.pendingToken(), req.code());
        refreshCookie.set(response, session.refreshToken());
        return ResponseEntity.ok(new TwoFaLoginResponse(
                session.token(),
                userRestMapper.toRestDto(userMapper.toDto(user))));
    }
}
