package com.example.walletbot.user.api;

import com.example.walletbot.platform.web.security.OtpVerifyRateLimiter;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.application.TwoFaService;
import com.example.walletbot.user.application.UserMapperImpl;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TwoFaControllerTest {

    private TwoFaService twoFaService;
    private MockMvc mvc;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        twoFaService = mock(TwoFaService.class);
        user = new UserEntity();
        user.setId(1L);
        user.setTelegramId(100L);
        OtpVerifyRateLimiter otpVerifyRateLimiter = mock(OtpVerifyRateLimiter.class);
        when(otpVerifyRateLimiter.isAllowed(1L)).thenReturn(true);
        TwoFaController controller = new TwoFaController(
                twoFaService, new UserMapperImpl(), new UserRestMapperImpl(), new RefreshCookie(false, 30L),
                otpVerifyRateLimiter);
        mvc = MiniAppMvc.forController(controller, user);
    }

    @Test
    void verifyLogin_validOtp_returnsTokens() throws Exception {
        when(twoFaService.resolveUserFromPendingToken("pending")).thenReturn(user);
        when(twoFaService.verifyLoginOtp(user, "pending", "123456"))
                .thenReturn(new TwoFaService.IssuedSession("ACCESS", "REFRESH"));

        mvc.perform(post("/api/miniapp/auth/2fa")
                        .contentType(APPLICATION_JSON)
                        .content("{\"pendingToken\":\"pending\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("ACCESS"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value("refresh", "REFRESH"))
                .andExpect(cookie().httpOnly("refresh", true));

        verify(twoFaService).verifyLoginOtp(user, "pending", "123456");
    }

    @Test
    void verifyLogin_shortCode_returns422Validation() throws Exception {
        mvc.perform(post("/api/miniapp/auth/2fa")
                        .contentType(APPLICATION_JSON)
                        .content("{\"pendingToken\":\"pending\",\"code\":\"12\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
