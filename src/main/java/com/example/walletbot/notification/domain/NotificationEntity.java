package com.example.walletbot.notification.domain;

import com.example.walletbot.shared.constant.NotificationType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "notifications")
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private NotificationType type;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 1000)
    private String body;

    @Column(name = "data_json", columnDefinition = "text")
    private String dataJson;

    @Column(name = "broadcast_id")
    private Long broadcastId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
