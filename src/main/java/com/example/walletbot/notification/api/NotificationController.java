package com.example.walletbot.notification.api;

import com.example.walletbot.notification.api.dto.NotificationDto;
import com.example.walletbot.notification.api.dto.NotificationPreferencesDto;
import com.example.walletbot.notification.api.dto.NotificationsPageResponse;
import com.example.walletbot.notification.api.dto.UpdateNotificationPreferencesRequest;
import com.example.walletbot.notification.application.NotificationMapper;
import com.example.walletbot.notification.application.NotificationService;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.user.application.UserService;
import com.example.walletbot.user.domain.NotificationPreferencesUpdate;
import com.example.walletbot.user.domain.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/miniapp/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;
    private final NotificationMapper notificationMapper;
    private final NotificationRestMapper notificationRestMapper;

    @GetMapping
    public NotificationsPageResponse list(@CurrentUser UserEntity user,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        var p = notificationService.getUserNotifications(user.getId(), page, size);
        var items = p.getContent().stream()
                .map(e -> notificationRestMapper.toRestDto(notificationMapper.toDto(e)))
                .toList();
        return new NotificationsPageResponse(items, p.getNumber(), p.getTotalPages(),
                notificationService.getUnreadCount(user.getId()));
    }

    @GetMapping("/{id}")
    public NotificationDto get(
            @CurrentUser UserEntity user, @PathVariable Long id) {
        var entity = notificationService.getNotification(user.getId(), id);
        return notificationRestMapper.toRestDto(notificationMapper.toDto(entity));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@CurrentUser UserEntity user) {
        return Map.of("count", notificationService.getUnreadCount(user.getId()));
    }

    @PostMapping("/mark-read")
    public Map<String, Boolean> markRead(@CurrentUser UserEntity user) {
        notificationService.markAllRead(user.getId());
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/mark-read")
    public Map<String, Boolean> markRead(@CurrentUser UserEntity user, @PathVariable Long id) {
        notificationService.markRead(user.getId(), id);
        return Map.of("ok", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@CurrentUser UserEntity user, @PathVariable Long id) {
        notificationService.deleteNotification(user.getId(), id);
        return Map.of("ok", true);
    }

    @GetMapping("/preferences")
    public NotificationPreferencesDto getPreferences(@CurrentUser UserEntity user) {
        return new NotificationPreferencesDto(
                user.isNotifyPushEnabled(), user.isNotifyEmailEnabled());
    }

    @PutMapping("/preferences")
    public NotificationPreferencesDto updatePreferences(
            @CurrentUser UserEntity user,
            @RequestBody UpdateNotificationPreferencesRequest req) {
        userService.updateNotificationPreferences(user, new NotificationPreferencesUpdate(
                req.pushEnabled(), req.emailEnabled()));
        return getPreferences(user);
    }
}
