package com.example.walletbot.notification.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "broadcast")
@Data
@NoArgsConstructor
public class BroadcastEntity {

    public enum BroadcastType {NEWS, APP_UPDATE}

    public enum BroadcastStatus {DRAFT, SENDING, SENT, FAILED}

    public enum BroadcastTarget {ALL, WITH_EMAIL}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BroadcastType type;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "body_ru", nullable = false, columnDefinition = "TEXT")
    private String bodyRu;

    @Column(name = "body_en", nullable = false, columnDefinition = "TEXT")
    private String bodyEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BroadcastTarget target = BroadcastTarget.ALL;

    @Column(nullable = false, length = 64)
    private String channels = "IN_APP,BOT";

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BroadcastStatus status = BroadcastStatus.DRAFT;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}