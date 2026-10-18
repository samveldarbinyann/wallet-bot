package com.example.walletbot.news.domain;

import java.util.List;

public record NewsPageResponse(
        List<NewsItemDto> news,
        int currentPage,
        int totalPages,
        String fetchedAt) {
}