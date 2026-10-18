package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.NotificationEntity;
import com.example.walletbot.notification.domain.NotificationRepository;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher notificationDispatcher;

    public void send(Long userId, NotificationType type, Map<String, Object> data,
                     String titleKey, String bodyKey, Object... bodyArgs) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationDispatcher.deliver(userId, type, data, titleKey, bodyKey, bodyArgs);
                }
            });
        } else {
            notificationDispatcher.deliver(userId, type, data, titleKey, bodyKey, bodyArgs);
        }
    }

    public Page<NotificationEntity> getUserNotifications(Long userId, int page, int size) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
    }

    public NotificationEntity getNotification(Long userId, Long id) {
        return notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        notificationRepository.markAsRead(id, userId);
    }

    @Transactional
    public void deleteNotification(Long userId, Long id) {
        NotificationEntity entity = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        notificationRepository.delete(entity);
    }
}
