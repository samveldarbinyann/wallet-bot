package com.example.walletbot.admin.api;

import com.example.walletbot.admin.application.AdminStatsService;
import com.example.walletbot.admin.domain.AdminStatsDto;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStatsControllerTest {

    private AdminStatsService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AdminStatsService.class);
        mvc = MiniAppMvc.forController(new AdminStatsController(service, new AdminStatsRestMapperImpl()), new UserEntity());
    }

    @Test
    void getTurnover_defaultPeriod_mapsDto() throws Exception {
        when(service.getStats("DAY")).thenReturn(new AdminStatsDto(
                BigDecimal.TEN, 1, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ONE, 10, 7, 2, 1));

        mvc.perform(get("/api/admin/stats/turnover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sendUsd").value(10))
                .andExpect(jsonPath("$.revenueUsd").value(1))
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.activeUsers").value(7));

        verify(service).getStats("DAY");
    }

    @Test
    void getTurnover_explicitPeriod_passedThrough() throws Exception {
        when(service.getStats("WEEK")).thenReturn(new AdminStatsDto(
                BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, 0, 0, 0));

        mvc.perform(get("/api/admin/stats/turnover").param("period", "WEEK"))
                .andExpect(status().isOk());

        verify(service).getStats("WEEK");
    }
}
