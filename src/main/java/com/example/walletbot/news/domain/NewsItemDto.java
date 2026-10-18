package com.example.walletbot.news.domain;

public record NewsItemDto(
        String id,
        String title,
        String url,
        String source,
        String description,
        String publishedAt,
        String image) {
}