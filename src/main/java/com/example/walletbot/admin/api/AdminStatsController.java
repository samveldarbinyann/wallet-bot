package com.example.walletbot.admin.api;

import com.example.walletbot.admin.api.dto.AdminStatsDto;
import com.example.walletbot.admin.application.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;
    private final AdminStatsRestMapper adminStatsRestMapper;

    @GetMapping("/turnover")
    public AdminStatsDto getTurnover(
            @RequestParam(defaultValue = "DAY") String period) {
        return adminStatsRestMapper.toRestDto(adminStatsService.getStats(period));
    }
}