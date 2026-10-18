package com.example.walletbot.user.api;

import com.example.walletbot.platform.web.security.OtpRateLimiter;
import com.example.walletbot.platform.web.security.OtpVerifyRateLimiter;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.application.SecurityService;
import com.example.walletbot.user.application.TwoFaService;
import com.example.walletbot.user.application.UserMapperImpl;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityControllerTest {

    private SecurityService securityService;
    private TwoFaService twoFaService;
    private OtpRateLimiter otpRateLimiter;
    private OtpVerifyRateLimiter otpVerifyRateLimiter;
    private MockMvc mvc;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        securityService = mock(SecurityService.class);
        twoFaService = mock(TwoFaService.class);
        otpRateLimiter = mock(OtpRateLimiter.class);
        otpVerifyRateLimiter = mock(OtpVerifyRateLimiter.class);

        user = new UserEntity();
        user.setId(1L);
        user.setTelegramId(100L);

        when(otpRateLimiter.isAllowed(any())).thenReturn(true);
        when(otpVerifyRateLimiter.isAllowed(any())).thenReturn(true);

        SecurityController controller = new SecurityController(securityService, twoFaService,
                otpRateLimiter, otpVerifyRateLimiter, new UserMapperImpl(), new UserRestMapperImpl(),
                new RefreshCookie(false, 30L));
        mvc = MiniAppMvc.forController(controller, user);
    }

    @Test
    void emailStart_validEmail_delegates() throws Exception {
        mvc.perform(post("/api/miniapp/security/email/start")
                        .contentType(APPLICATION_JSON).content("{\"email\":\"sam@example.com\"}"))
                .andExpect(status().isOk());

        verify(securityService).initiateEmailBinding(user, "sam@example.com");
    }

    @Test
    void emailStart_rateLimited_returns429() throws Exception {
        when(otpRateLimiter.isAllowed(1L)).thenReturn(false);

        mvc.perform(post("/api/miniapp/security/email/start")
                        .contentType(APPLICATION_JSON).content("{\"email\":\"sam@example.com\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void emailStart_invalidEmail_returns422() throws Exception {
        mvc.perform(post("/api/miniapp/security/email/start")
                        .contentType(APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emailConfirm_delegatesWithCode() throws Exception {
        mvc.perform(post("/api/miniapp/security/email/confirm")
                        .contentType(APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk());

        verify(securityService).confirmEmailBinding(user, "123456");
    }

    @Test
    void emailRemove_delegates() throws Exception {
        mvc.perform(delete("/api/miniapp/security/email"))
                .andExpect(status().isOk());

        verify(securityService).removeEmail(user);
    }

    @Test
    void pinSetupConfirm_delegatesWithOtpAndPin() throws Exception {
        mvc.perform(post("/api/miniapp/security/pin/setup/confirm")
                        .contentType(APPLICATION_JSON).content("{\"otpCode\":\"123456\",\"pin\":\"654321\"}"))
                .andExpect(status().isOk());

        verify(securityService).confirmPinSetup(user, "123456", "654321");
    }

    @Test
    void pinChange_delegates() throws Exception {
        mvc.perform(post("/api/miniapp/security/pin/change")
                        .contentType(APPLICATION_JSON).content("{\"currentPin\":\"111111\",\"newPin\":\"222222\"}"))
                .andExpect(status().isOk());

        verify(securityService).changePin(user, "111111", "222222");
    }

    @Test
    void pinDisable_invalidPinFormat_returns422() throws Exception {
        mvc.perform(delete("/api/miniapp/security/pin")
                        .contentType(APPLICATION_JSON).content("{\"currentPin\":\"abc\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pinVerify_tokenFlow_verifiesAndIssuesSession() throws Exception {
        when(securityService.verifyPinByPendingToken("pin-tok", "123456")).thenReturn(user);
        when(twoFaService.issueTokenOrGate(user)).thenReturn(new TwoFaService.IssuedSession("ACCESS", "REFRESH"));

        mvc.perform(post("/api/miniapp/security/pin/verify")
                        .contentType(APPLICATION_JSON).content("{\"pinPendingToken\":\"pin-tok\",\"pin\":\"123456\"}"))
                .andExpect(status().isOk());

        verify(securityService).verifyPinByPendingToken("pin-tok", "123456");
    }

}
