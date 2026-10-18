package com.example.walletbot.wallet.api;

import com.example.walletbot.asset.application.AssetService;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.api.dto.PortfolioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class PortfolioController {

    private final AssetService assetService;

    @GetMapping("/portfolio")
    public PortfolioResponse getPortfolio(@CurrentUser UserEntity user) {
        AssetService.PortfolioSummary s = assetService.buildPortfolio(user);
        return new PortfolioResponse(s.totalUsd(), s.change24hUsd(), s.change24hPercent(), s.items());
    }
}
