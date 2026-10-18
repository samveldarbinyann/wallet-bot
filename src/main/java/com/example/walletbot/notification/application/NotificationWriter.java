package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.NotificationEntity;
import com.example.walletbot.notification.domain.NotificationRepository;
import com.example.walletbot.shared.constant.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationWriter {

    private static final int TITLE_LIMIT = 200;
    private static final int BODY_LIMIT = 1000;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    public void writeInApp(Long userId, NotificationType type, String title, String body,
                           Map<String, Object> data) {
        writeInApp(userId, type, title, body, data, null);
    }

    public void writeInApp(Long userId, NotificationType type, String title, String body,
                           Map<String, Object> data, Long broadcastId) {
        NotificationEntity n = new NotificationEntity();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(truncate(title, TITLE_LIMIT));
        n.setBody(truncate(body, BODY_LIMIT));
        n.setDataJson(serialize(data));
        n.setBroadcastId(broadcastId);
        notificationRepository.save(n);
    }

    private String serialize(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize notification data: {}", e.getMessage());
            return null;
        }
    }
}
