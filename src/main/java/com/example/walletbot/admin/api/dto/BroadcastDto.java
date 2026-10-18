package com.example.walletbot.admin.api.dto;

public record BroadcastDto(
        Long id,
        Long createdBy,
        String type,
        String titleRu,
        String titleEn,
        String bodyRu,
        String bodyEn,
        String target,
        String channels,
        String imageUrl,
        String status,
        int sentCount,
        String createdAt,
        String sentAt
) {
}