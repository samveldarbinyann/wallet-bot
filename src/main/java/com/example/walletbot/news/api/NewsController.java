package com.example.walletbot.news.api;

import com.example.walletbot.news.application.RssFeedService;
import com.example.walletbot.news.domain.NewsPageResponse;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.user.domain.UserEntity;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class NewsController {

    private final RssFeedService rssFeedService;

    @GetMapping("/news")
    public NewsPageResponse getNews(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @CurrentUser UserEntity user
    ) {
        return rssFeedService.getNews(page, user.getLang());
    }
}