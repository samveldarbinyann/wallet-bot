package com.example.walletbot.notification.domain;

import java.time.Instant;

public record BroadcastDto(
        Long id,
        Long createdBy,
        BroadcastEntity.BroadcastType type,
        String titleRu,
        String titleEn,
        String bodyRu,
        String bodyEn,
        BroadcastEntity.BroadcastTarget target,
        String channels,
        String imageUrl,
        BroadcastEntity.BroadcastStatus status,
        int sentCount,
        Instant createdAt,
        Instant sentAt
) {
}
