package com.example.walletbot.asset.api;

import com.example.walletbot.asset.application.ChartService;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChartControllerTest {

    private ChartService chartService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        chartService = mock(ChartService.class);
        mvc = MiniAppMvc.forController(new ChartController(chartService), new UserEntity());
    }

    @Test
    void getChart_defaultDays_delegatesWithSeven() throws Exception {
        when(chartService.getChart("BTC", "7")).thenReturn(List.of(List.of(new BigDecimal("1"), new BigDecimal("2"))));

        mvc.perform(get("/api/miniapp/chart/BTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0][0]").value(1));

        verify(chartService).getChart("BTC", "7");
    }

    @Test
    void getChart_explicitDays_passedThrough() throws Exception {
        when(chartService.getChart(eq("ETH"), eq("30"))).thenReturn(List.of());

        mvc.perform(get("/api/miniapp/chart/ETH").param("days", "30"))
                .andExpect(status().isOk());

        verify(chartService).getChart("ETH", "30");
    }

    @Test
    void getChart_invalidDays_returns400() throws Exception {
        when(chartService.getChart("BTC", "99")).thenThrow(new BadRequestException("error.chart_invalid_days"));

        mvc.perform(get("/api/miniapp/chart/BTC").param("days", "99"))
                .andExpect(status().isBadRequest());
    }
}
