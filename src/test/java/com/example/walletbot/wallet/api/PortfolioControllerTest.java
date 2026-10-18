package com.example.walletbot.wallet.api;

import com.example.walletbot.asset.application.AssetService;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioControllerTest {

    private final UserEntity user = new UserEntity();
    private AssetService assetService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        assetService = mock(AssetService.class);
        mvc = MiniAppMvc.forController(new PortfolioController(assetService), user);
    }

    @Test
    void getPortfolio_mapsSummaryToResponse() throws Exception {
        when(assetService.buildPortfolio(user)).thenReturn(new AssetService.PortfolioSummary(
                new BigDecimal("123.45"), new BigDecimal("5.00"), new BigDecimal("4.2"), List.of()));

        mvc.perform(get("/api/miniapp/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value(123.45))
                .andExpect(jsonPath("$.change24hUsd").value(5.00))
                .andExpect(jsonPath("$.change24hPercent").value(4.2));
    }
}
