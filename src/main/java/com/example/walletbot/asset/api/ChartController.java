package com.example.walletbot.asset.api;

import com.example.walletbot.asset.application.ChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class ChartController {

    private final ChartService chartService;

    @GetMapping("/chart/{symbol}")
    public List<List<BigDecimal>> getChart(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "7") String days
    ) {
        return chartService.getChart(symbol, days);
    }
}