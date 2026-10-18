package com.example.walletbot.admin.api;

import com.example.walletbot.admin.application.AdminUserMapperImpl;
import com.example.walletbot.admin.application.AdminUserService;
import com.example.walletbot.platform.web.security.AdminMutationRateLimiter;
import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {

    private final UserEntity admin = new UserEntity();
    private AdminUserService adminUserService;
    private AdminMutationRateLimiter rateLimiter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        admin.setId(1L);
        admin.setTelegramId(100L);
        adminUserService = mock(AdminUserService.class);
        rateLimiter = mock(AdminMutationRateLimiter.class);
        AdminUserController controller = new AdminUserController(
                adminUserService, rateLimiter, new AdminUserMapperImpl(), new AdminUserRestMapperImpl());
        mvc = MiniAppMvc.forController(controller, admin);
    }

    private UserEntity target() {
        UserEntity u = new UserEntity();
        u.setId(2L);
        u.setTelegramId(200L);
        u.setStatus(UserStatus.BLOCKED);
        return u;
    }

    @Test
    void blockUser_delegates() throws Exception {
        when(adminUserService.blockUser(1L, 2L, "fraud")).thenReturn(target());

        mvc.perform(post("/api/admin/users/2/block")
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"fraud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("2"));

        verify(adminUserService).blockUser(1L, 2L, "fraud");
    }

    @Test
    void blockUser_rateLimited_returns429() throws Exception {
        doThrow(new RateLimitException("Admin mutation rate limit exceeded"))
                .when(rateLimiter).assertAllowed(1L);

        mvc.perform(post("/api/admin/users/2/block"))
                .andExpect(status().isTooManyRequests());

        verify(adminUserService, never()).blockUser(any(), any(), any());
    }

    @Test
    void freezeUser_noBody_passesNullReason() throws Exception {
        when(adminUserService.freezeUser(1L, 2L, null)).thenReturn(target());

        mvc.perform(post("/api/admin/users/2/freeze"))
                .andExpect(status().isOk());

        verify(adminUserService).freezeUser(1L, 2L, null);
    }

    @Test
    void forceLogout_returnsOk() throws Exception {
        mvc.perform(post("/api/admin/users/2/force-logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(adminUserService).forceLogout(1L, 2L);
    }
}
