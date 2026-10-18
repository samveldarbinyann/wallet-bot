package com.example.walletbot.user.api;

import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.platform.web.security.AuthRateLimiter;
import com.example.walletbot.platform.web.security.TelegramAuthService;
import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.application.*;
import com.example.walletbot.user.domain.UserEntity;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private TelegramAuthService telegramAuthService;
    private UserService userService;
    private AuthRateLimiter authRateLimiter;
    private TwoFaService twoFaService;
    private MonitoringService monitoringService;
    private SecurityService securityService;
    private MockMvc mvc;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        telegramAuthService = mock(TelegramAuthService.class);
        userService = mock(UserService.class);
        authRateLimiter = mock(AuthRateLimiter.class);
        twoFaService = mock(TwoFaService.class);
        monitoringService = mock(MonitoringService.class);
        securityService = mock(SecurityService.class);

        user = new UserEntity();
        user.setId(1L);
        user.setTelegramId(100L);
        user.setStatus(UserStatus.ACTIVE);

        when(authRateLimiter.isAllowed(any(HttpServletRequest.class))).thenReturn(true);
        when(telegramAuthService.validate(any()))
                .thenReturn(new TelegramAuthService.TelegramAuthResult(100L, "sam", "Sam", "D", "en"));
        when(userService.getOrCreateUser(any(), any(), any(), any(), any())).thenReturn(user);

        AuthController controller = new AuthController(
                telegramAuthService, userService, authRateLimiter, twoFaService, monitoringService,
                new UserMapperImpl(), new UserRestMapperImpl(), securityService,
                new RefreshCookie(false, 30L), new UserStatusGuard());
        mvc = MiniAppMvc.forController(controller, user);
    }

    @Test
    void authenticate_tokenIssued_returns200WithToken() throws Exception {
        when(twoFaService.resolveAuthSession(user)).thenReturn(
                new TwoFaService.AuthSessionResult.TokenIssued(
                        new TwoFaService.IssuedSession("ACCESS", "REFRESH")));

        mvc.perform(post("/api/miniapp/auth")
                        .contentType(APPLICATION_JSON).content("{\"initData\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("ACCESS"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value("refresh", "REFRESH"))
                .andExpect(cookie().httpOnly("refresh", true));

        verify(userService).updateLastLoginAt(user);
    }

    @Test
    void authenticate_pinRequired_returns202() throws Exception {
        when(twoFaService.resolveAuthSession(user)).thenReturn(
                new TwoFaService.AuthSessionResult.PinRequired("pin-token", "s***@x.com"));

        mvc.perform(post("/api/miniapp/auth")
                        .contentType(APPLICATION_JSON).content("{\"initData\":\"x\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pinPendingToken").value("pin-token"));
    }

    @Test
    void authenticate_rateLimited_returns429AndCountsFailure() throws Exception {
        when(authRateLimiter.isAllowed(any(HttpServletRequest.class))).thenReturn(false);

        mvc.perform(post("/api/miniapp/auth")
                        .contentType(APPLICATION_JSON).content("{\"initData\":\"x\"}"))
                .andExpect(status().isTooManyRequests());

        verify(monitoringService).incrementAuthFailed();
    }

    @Test
    void authenticate_blockedUser_returns403() throws Exception {
        user.setStatus(UserStatus.BLOCKED);
        user.setStatusReason("banned");

        mvc.perform(post("/api/miniapp/auth")
                        .contentType(APPLICATION_JSON).content("{\"initData\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticate_emptyInitData_returns422Validation() throws Exception {
        mvc.perform(post("/api/miniapp/auth")
                        .contentType(APPLICATION_JSON).content("{\"initData\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refresh_readsCookie_returnsNewAccess() throws Exception {
        when(securityService.resolveRefreshUser("OLD")).thenReturn(user);
        when(securityService.mintAccessToken(user)).thenReturn("NEW_ACCESS");

        mvc.perform(post("/api/miniapp/auth/refresh").cookie(new Cookie("refresh", "OLD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("NEW_ACCESS"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void refresh_missingCookie_delegatesNullToService() throws Exception {
        when(securityService.resolveRefreshUser(isNull()))
                .thenThrow(new BadRequestException("error.invalid_refresh_token"));

        mvc.perform(post("/api/miniapp/auth/refresh"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_pinEnabled_withoutSessionProof_reentersPinGate() throws Exception {
        user.setPinEnabled(true);
        when(securityService.resolveRefreshUser("OLD")).thenReturn(user);
        when(securityService.requiresLoginGate(user, null)).thenReturn(true);
        when(twoFaService.resolveAuthSession(user)).thenReturn(
                new TwoFaService.AuthSessionResult.PinRequired("pin-token", "s***@x.com"));

        mvc.perform(post("/api/miniapp/auth/refresh").cookie(new Cookie("refresh", "OLD")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("PIN_PENDING"))
                .andExpect(jsonPath("$.pinPendingToken").value("pin-token"));

        verify(securityService, never()).mintAccessToken(any());
    }

    @Test
    void refresh_pinEnabled_withStaleAccessProof_returnsNewAccess() throws Exception {
        user.setPinEnabled(true);
        when(securityService.resolveRefreshUser("OLD")).thenReturn(user);
        when(securityService.requiresLoginGate(user, "STALE")).thenReturn(false);
        when(securityService.mintAccessToken(user)).thenReturn("NEW_ACCESS");

        mvc.perform(post("/api/miniapp/auth/refresh")
                        .cookie(new Cookie("refresh", "OLD"))
                        .header("Authorization", "Bearer STALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("NEW_ACCESS"));

        verify(twoFaService, never()).resolveAuthSession(any());
    }

    @Test
    void refresh_pinEnabled_withForeignAccessToken_reentersPinGate() throws Exception {
        user.setPinEnabled(true);
        when(securityService.resolveRefreshUser("OLD")).thenReturn(user);
        when(securityService.requiresLoginGate(user, "FOREIGN")).thenReturn(true);
        when(twoFaService.resolveAuthSession(user)).thenReturn(
                new TwoFaService.AuthSessionResult.PinRequired("pin-token", null));

        mvc.perform(post("/api/miniapp/auth/refresh")
                        .cookie(new Cookie("refresh", "OLD"))
                        .header("Authorization", "Bearer FOREIGN"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("PIN_PENDING"));
    }

    @Test
    void refresh_noGates_withoutProof_returnsNewAccess() throws Exception {
        when(securityService.resolveRefreshUser("OLD")).thenReturn(user);
        when(securityService.requiresLoginGate(user, null)).thenReturn(false);
        when(securityService.mintAccessToken(user)).thenReturn("NEW_ACCESS");

        mvc.perform(post("/api/miniapp/auth/refresh").cookie(new Cookie("refresh", "OLD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("NEW_ACCESS"));

        verify(twoFaService, never()).resolveAuthSession(any());
    }
}
