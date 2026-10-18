package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.PushNotificationSender;
import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    UserRepository userRepository;
    @Mock
    EmailService emailService;
    @Mock
    MessageService messageService;
    @Mock
    PushNotificationSender pushNotificationSender;
    @Mock
    MonitoringService monitoringService;
    @Mock
    NotificationWriter notificationWriter;
    @InjectMocks
    NotificationDispatcher dispatcher;

    private UserEntity user(boolean push, boolean email, boolean emailVerified) {
        UserEntity u = new UserEntity();
        u.setId(1L);
        u.setTelegramId(100L);
        u.setLang("en");
        u.setNotifyPushEnabled(push);
        u.setNotifyEmailEnabled(email);
        if (emailVerified) {
            u.setEmail("user@example.com");
            u.setEmailVerifiedAt(Instant.now());
        }
        return u;
    }

    @Test
    void deliver_userNotFound_doesNothing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        dispatcher.deliver(1L, NotificationType.DEPOSIT, null, "t", "b");
        verifyNoInteractions(notificationWriter, pushNotificationSender, emailService);
    }

    @Test
    void deliver_alwaysWritesInApp_evenWithEveryChannelOff() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false, false, false)));
        dispatcher.deliver(1L, NotificationType.DEPOSIT, null, "t", "b");
        verify(notificationWriter).writeInApp(eq(1L), eq(NotificationType.DEPOSIT), any(), any(), any());
    }

    @Test
    void deliver_pushEnabled_sendsPush() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(true, false, false)));
        dispatcher.deliver(1L, NotificationType.DEPOSIT, null, "t", "b");
        verify(pushNotificationSender).send(eq(100L), any(), any(), any(), any());
    }

    @Test
    void deliver_push_usesBotVariant_whileInAppKeepsPlain() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(true, false, false)));
        when(messageService.get("en", "t")).thenReturn("Plain Title");
        when(messageService.get("en", "b")).thenReturn("Plain Body");
        when(messageService.has("en", "t.bot")).thenReturn(true);
        when(messageService.has("en", "b.bot")).thenReturn(true);
        when(messageService.get("en", "t.bot")).thenReturn("✅ Bot Title");
        when(messageService.get("en", "b.bot")).thenReturn("Bot Body");

        dispatcher.deliver(1L, NotificationType.DEPOSIT, null, "t", "b");

        verify(pushNotificationSender).send(100L, "✅ Bot Title", "Bot Body", "en", null);
        verify(notificationWriter).writeInApp(eq(1L), eq(NotificationType.DEPOSIT),
                eq("Plain Title"), eq("Plain Body"), any());
    }

    @Test
    void deliver_push_noBotVariant_fallsBackToPlain() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(true, false, false)));
        when(messageService.get("en", "t")).thenReturn("Plain Title");
        when(messageService.get("en", "b")).thenReturn("Plain Body");

        dispatcher.deliver(1L, NotificationType.DEPOSIT, null, "t", "b");

        verify(pushNotificationSender).send(100L, "Plain Title", "Plain Body", "en", null);
    }

    @Test
    void deliver_pushDisabled_nonCriticalType_skipsPush() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false, false, false)));
        dispatcher.deliver(1L, NotificationType.DEPOSIT, null, "t", "b");
        verify(pushNotificationSender, never()).send(anyLong(), any(), any(), any(), any());
    }

    @Test
    void deliver_pushDisabled_criticalType_stillSendsPush() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false, false, false)));
        dispatcher.deliver(1L, NotificationType.ACCOUNT_BLOCKED, null, "t", "b");
        verify(pushNotificationSender).send(eq(100L), any(), any(), any(), any());
    }

    @Test
    void deliver_emailEnabledAndVerified_sendsEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false, true, true)));
        dispatcher.deliver(1L, NotificationType.DEPOSIT, null, "t", "b");
        verify(emailService).sendNotification(eq("user@example.com"), any(), any(), any());
    }

    @Test
    void deliver_emailDisabled_nonCriticalType_skipsEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false, false, true)));
        dispatcher.deliver(1L, NotificationType.DEPOSIT, null, "t", "b");
        verify(emailService, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void deliver_emailDisabled_criticalType_stillSendsEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false, false, true)));
        dispatcher.deliver(1L, NotificationType.ACCOUNT_FROZEN, null, "t", "b");
        verify(emailService).sendNotification(eq("user@example.com"), any(), any(), any());
    }

    @Test
    void deliver_emailEnabledButNotVerified_skipsEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false, true, false)));
        dispatcher.deliver(1L, NotificationType.DEPOSIT, null, "t", "b");
        verify(emailService, never()).sendNotification(any(), any(), any(), any());
    }
}
