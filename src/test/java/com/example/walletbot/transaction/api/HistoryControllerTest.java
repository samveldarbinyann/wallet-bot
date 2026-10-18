package com.example.walletbot.transaction.api;

import com.example.walletbot.deposit.application.DepositScanService;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.transaction.application.HistoryService;
import com.example.walletbot.transaction.domain.TransactionDto;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HistoryControllerTest {

    private final UserEntity user = new UserEntity();
    private DepositScanService depositScanService;
    private HistoryService historyService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        depositScanService = mock(DepositScanService.class);
        historyService = mock(HistoryService.class);
        when(historyService.findUnifiedHistory(eq(1L), anyInt(), anyInt())).thenReturn(Page.empty());
        mvc = MiniAppMvc.forController(new HistoryController(depositScanService, historyService), user);
    }

    @Test
    void getHistory_firstPageWithScan_triggersDepositScan() throws Exception {
        mvc.perform(get("/api/miniapp/history").param("page", "0").param("scan", "true"))
                .andExpect(status().isOk());

        verify(depositScanService).scanUserWalletsAsync(1L);
    }

    @Test
    void getHistory_scanFalse_doesNotScan() throws Exception {
        mvc.perform(get("/api/miniapp/history").param("page", "0").param("scan", "false"))
                .andExpect(status().isOk());

        verify(depositScanService, never()).scanUserWalletsAsync(anyLong());
    }

    @Test
    void getHistory_nonFirstPage_doesNotScan() throws Exception {
        mvc.perform(get("/api/miniapp/history").param("page", "2").param("scan", "true"))
                .andExpect(status().isOk());

        verify(depositScanService, never()).scanUserWalletsAsync(anyLong());
    }

    @Test
    void getTransaction_delegatesToService() throws Exception {
        when(historyService.getTransaction(1L, 55L)).thenReturn(new TransactionDto(
                "55", "SEND", null, "ETH", "ETH", "SUCCESS", "0xto", "0xhash", "2026-05-30", null));

        mvc.perform(get("/api/miniapp/transactions/55"))
                .andExpect(status().isOk());

        verify(historyService).getTransaction(1L, 55L);
    }
}
