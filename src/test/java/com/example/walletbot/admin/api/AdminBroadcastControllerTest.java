package com.example.walletbot.admin.api;

import com.example.walletbot.admin.application.AdminBroadcastService;
import com.example.walletbot.notification.domain.BroadcastDto;
import com.example.walletbot.platform.web.security.AdminMutationRateLimiter;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBroadcastControllerTest {

    private static final String VALID_BODY =
            "{\"type\":\"NEWS\",\"titleRu\":\"тР\",\"titleEn\":\"tE\",\"bodyRu\":\"бР\",\"bodyEn\":\"bE\"," +
                    "\"target\":\"ALL\",\"channels\":\"PUSH\",\"imageUrl\":null}";
    private final UserEntity admin = new UserEntity();
    private AdminBroadcastService broadcastService;
    private AdminMutationRateLimiter rateLimiter;
    private AdminBroadcastController controller;
    private MockMvc mvc;

    private static BroadcastDto dto(long id) {
        return new BroadcastDto(id, 1L, null, null, null, null, null, null, null, null, null, 0, null, null);
    }

    @BeforeEach
    void setUp() {
        admin.setId(1L);
        broadcastService = mock(AdminBroadcastService.class);
        rateLimiter = mock(AdminMutationRateLimiter.class);
        BroadcastRestMapper broadcastRestMapper = mock(BroadcastRestMapper.class);

        controller = new AdminBroadcastController(broadcastService, rateLimiter, broadcastRestMapper);
        mvc = MiniAppMvc.forController(controller, admin);
    }

    @Test
    void create_valid_delegates() throws Exception {
        when(broadcastService.create(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dto(1L));

        mvc.perform(post("/api/admin/broadcasts").contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());

        verify(broadcastService).create(eq(1L), eq("NEWS"), any(), any(), any(), any(), eq("ALL"), eq("PUSH"), any());
    }

    @Test
    void create_rateLimited_returns429() throws Exception {
        doThrow(new RateLimitException("Admin mutation rate limit exceeded"))
                .when(rateLimiter).assertAllowed(1L);

        mvc.perform(post("/api/admin/broadcasts").contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isTooManyRequests());

        verify(broadcastService, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_invalidType_returns422() throws Exception {
        String bad = VALID_BODY.replace("NEWS", "BOGUS");
        mvc.perform(post("/api/admin/broadcasts").contentType(APPLICATION_JSON).content(bad))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void send_delegates() throws Exception {
        when(broadcastService.send(9L)).thenReturn(dto(9L));

        mvc.perform(post("/api/admin/broadcasts/9/send"))
                .andExpect(status().isOk());

        verify(broadcastService).send(9L);
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete("/api/admin/broadcasts/9"))
                .andExpect(status().isNoContent());

        verify(broadcastService).delete(9L);
    }

    @Test
    void get_delegates() throws Exception {
        when(broadcastService.get(9L)).thenReturn(dto(9L));

        mvc.perform(get("/api/admin/broadcasts/9"))
                .andExpect(status().isOk());

        verify(broadcastService).get(9L);
    }

    @Test
    void list_delegatesAtMethodLevel() {
        when(broadcastService.list(anyInt(), anyInt())).thenReturn(Page.empty());

        controller.list(0, 20);

        verify(broadcastService).list(0, 20);
    }
}
