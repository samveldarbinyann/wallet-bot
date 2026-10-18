package com.example.walletbot.export.api;

import com.example.walletbot.export.application.ExportService;
import com.example.walletbot.export.domain.ExportRequestDto;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExportControllerTest {

    private final UserEntity user = new UserEntity();
    private ExportService exportService;
    private ExportRestMapper exportRestMapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        exportService = mock(ExportService.class);
        exportRestMapper = mock(ExportRestMapper.class);
        mvc = MiniAppMvc.forController(new ExportController(exportService, exportRestMapper), user);
    }

    @Test
    void request_returnsOkWithNoStoreHeaders() throws Exception {
        when(exportService.request(user)).thenReturn(mock(ExportRequestDto.class));

        mvc.perform(post("/api/miniapp/security/export/request"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, private"));

        verify(exportService).request(user);
    }

    @Test
    void confirm_delegatesWithTokenAndOtp() throws Exception {
        mvc.perform(post("/api/miniapp/security/export/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"exportPendingToken\":\"tok\",\"otpCode\":\"123456\"}"))
                .andExpect(status().isOk());

        verify(exportService).confirm(eq(user), eq("tok"), eq("123456"));
    }

    @Test
    void confirm_shortOtp_returns422() throws Exception {
        mvc.perform(post("/api/miniapp/security/export/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"exportPendingToken\":\"tok\",\"otpCode\":\"12\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reveal_delegatesWithSessionAndWalletId() throws Exception {
        mvc.perform(post("/api/miniapp/security/export/reveal")
                        .contentType(APPLICATION_JSON)
                        .content("{\"exportSessionToken\":\"sess\",\"walletId\":50}"))
                .andExpect(status().isOk());

        verify(exportService).reveal(eq(user), eq("sess"), eq(50L));
    }

    @Test
    void close_returns204AndDelegates() throws Exception {
        mvc.perform(post("/api/miniapp/security/export/close")
                        .contentType(APPLICATION_JSON)
                        .content("{\"exportSessionToken\":\"sess\"}"))
                .andExpect(status().isNoContent());

        verify(exportService).close(user, "sess");
    }
}
