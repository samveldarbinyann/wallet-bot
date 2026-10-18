package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.PushNotificationSender;
import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final MessageService messageService;
    private final PushNotificationSender pushNotificationSender;
    private final MonitoringService monitoringService;
    private final NotificationWriter notificationWriter;

    @Async("ioExecutor")
    public void deliver(Long userId, NotificationType type, Map<String, Object> data,
                        String titleKey, String bodyKey, Object... bodyArgs) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        String lang = user.getLang();
        String title = messageService.get(lang, titleKey);
        String body = messageService.get(lang, bodyKey, bodyArgs);

        Map<String, Object> enrichedData = data != null ? new HashMap<>(data) : new HashMap<>();
        enrichedData.put("_titleKey", titleKey);
        enrichedData.put("_bodyKey", bodyKey);
        if (bodyArgs.length > 0) {
            enrichedData.put("_bodyArgs", bodyArgs);
        }
        notificationWriter.writeInApp(userId, type, title, body, enrichedData);
        monitoringService.incrementNotificationSent();

        boolean critical = type.isCritical();
        String explorerUrl = data != null ? (String) data.get("explorerUrl") : null;

        if (critical || user.isNotifyPushEnabled()) {
            String botTitle = messageService.has(lang, titleKey + ".bot")
                    ? messageService.get(lang, titleKey + ".bot") : title;
            String botBody = messageService.has(lang, bodyKey + ".bot")
                    ? messageService.get(lang, bodyKey + ".bot", bodyArgs) : body;
            pushNotificationSender.send(user.getTelegramId(), botTitle, botBody, lang, explorerUrl);
        }

        boolean emailAllowed = critical || user.isNotifyEmailEnabled();
        if (emailAllowed && user.isEmailDeliverable()) {
            try {
                emailService.sendNotification(user.getEmail(), title, body, lang);
            } catch (Exception e) {
                log.warn("Email notification failed for user {}: {}", userId, e.getMessage());
            }
        }
    }
}
