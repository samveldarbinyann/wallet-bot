package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.BroadcastEntity;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastStatus;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastTarget;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastType;
import com.example.walletbot.notification.domain.BroadcastRepository;
import com.example.walletbot.notification.domain.PushNotificationSender;
import com.example.walletbot.notification.infrastructure.BroadcastImageStorage;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.shared.constant.SupportedLanguage;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastDispatcher {

    private static final int PAGE_SIZE = 200;
    private static final long PUSH_THROTTLE_MS = 40;

    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final NotificationWriter notificationWriter;
    private final PushNotificationSender pushNotificationSender;
    private final EmailService emailService;
    private final BroadcastImageStorage imageStorage;

    @Async("ioExecutor")
    public void dispatch(Long broadcastId) {
        BroadcastEntity broadcast = broadcastRepository.findById(broadcastId).orElse(null);
        if (broadcast == null) {
            log.warn("Broadcast {} vanished before dispatch", broadcastId);
            return;
        }
        try {
            Set<String> channels = Arrays.stream(broadcast.getChannels().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            boolean sendBot = channels.contains("BOT");
            boolean sendEmail = channels.contains("EMAIL");
            boolean sendInApp = channels.contains("IN_APP");

            String imageUrl = broadcast.getImageUrl();
            Map<String, Object> data = imageUrl != null ? Map.of("image", imageUrl) : null;
            byte[] photo = imageUrl != null ? imageStorage.load(imageUrl) : null;
            String photoName = imageUrl != null ? imageStorage.filenameOf(imageUrl) : null;
            NotificationType type = mapType(broadcast.getType());

            String photoFileId = null;
            int count = 0;
            int page = 0;
            Page<UserEntity> userPage;
            do {
                userPage = loadUserPage(broadcast.getTarget(),
                        PageRequest.of(page, PAGE_SIZE, Sort.by("id")));
                for (UserEntity user : userPage.getContent()) {
                    try {
                        boolean ru = SupportedLanguage.fromTag(user.getLang()) == SupportedLanguage.ru;
                        String title = ru ? broadcast.getTitleRu() : broadcast.getTitleEn();
                        String body = ru ? broadcast.getBodyRu() : broadcast.getBodyEn();

                        if (sendInApp) {
                            notificationWriter.writeInApp(user.getId(), type, title, body, data, broadcastId);
                        }
                        if (sendBot && user.isNotifyPushEnabled()) {
                            throttle();
                            photoFileId = sendPush(user, title, body, photo, photoName, photoFileId);
                        }
                        if (sendEmail && user.isEmailDeliverable() && user.isNotifyEmailEnabled()) {
                            try {
                                emailService.sendNotification(user.getEmail(), title, body, user.getLang());
                            } catch (Exception e) {
                                log.warn("Email broadcast failed for user {}: {}", user.getId(), e.getMessage());
                            }
                        }
                        count++;
                    } catch (Exception e) {
                        log.warn("Broadcast delivery failed for user {}: {}", user.getId(), e.getMessage());
                    }
                }
                page++;
            } while (userPage.hasNext());

            broadcast.setStatus(BroadcastStatus.SENT);
            broadcast.setSentCount(count);
            broadcast.setSentAt(Instant.now());
            broadcastRepository.save(broadcast);
        } catch (Exception e) {
            log.error("Broadcast {} failed: {}", broadcastId, e.getMessage());
            broadcast.setStatus(BroadcastStatus.FAILED);
            broadcastRepository.save(broadcast);
        }
    }

    private Page<UserEntity> loadUserPage(BroadcastTarget target, Pageable pageable) {
        return target == BroadcastTarget.WITH_EMAIL
                ? userRepository.findByEmailIsNotNullAndEmailVerifiedAtIsNotNull(pageable)
                : userRepository.findAll(pageable);
    }

    private String sendPush(UserEntity user, String title, String body,
                            byte[] photo, String photoName, String photoFileId) {
        String lang = user.getLang();
        if (photo == null) {
            pushNotificationSender.send(user.getTelegramId(), title, body, lang);
            return photoFileId;
        }
        if (photoFileId != null) {
            pushNotificationSender.sendPhoto(user.getTelegramId(), title, body, lang, photoFileId);
            return photoFileId;
        }
        return pushNotificationSender.sendPhoto(user.getTelegramId(), title, body, lang, photo, photoName);
    }

    private NotificationType mapType(BroadcastType type) {
        return type == BroadcastType.NEWS ? NotificationType.NEWS : NotificationType.APP_UPDATE;
    }

    private void throttle() {
        try {
            Thread.sleep(PUSH_THROTTLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
