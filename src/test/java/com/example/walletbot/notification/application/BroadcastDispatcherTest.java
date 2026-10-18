package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.BroadcastEntity;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastStatus;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastTarget;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastType;
import com.example.walletbot.notification.domain.BroadcastRepository;
import com.example.walletbot.notification.domain.PushNotificationSender;
import com.example.walletbot.notification.infrastructure.BroadcastImageStorage;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BroadcastDispatcherTest {

    @Mock
    BroadcastRepository broadcastRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    NotificationWriter notificationWriter;
    @Mock
    PushNotificationSender pushNotificationSender;
    @Mock
    EmailService emailService;
    @Mock
    BroadcastImageStorage imageStorage;
    @InjectMocks
    BroadcastDispatcher dispatcher;

    private UserEntity user(long id, boolean push) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setTelegramId(1000 + id);
        u.setLang("en");
        u.setNotifyPushEnabled(push);
        return u;
    }

    private BroadcastEntity broadcast(String channels) {
        BroadcastEntity b = new BroadcastEntity();
        b.setId(5L);
        b.setType(BroadcastType.NEWS);
        b.setTitleEn("Title");
        b.setBodyEn("Body");
        b.setTitleRu("Заголовок");
        b.setBodyRu("Тело");
        b.setTarget(BroadcastTarget.ALL);
        b.setChannels(channels);
        return b;
    }

    private void stubUsers(UserEntity... users) {
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(users)));
    }

    @Test
    void dispatch_unknownBroadcast_doesNothing() {
        when(broadcastRepository.findById(5L)).thenReturn(Optional.empty());
        dispatcher.dispatch(5L);
        verifyNoInteractions(notificationWriter, pushNotificationSender, emailService);
    }

    @Test
    void dispatch_inAppChannel_writesForEveryUser() {
        when(broadcastRepository.findById(5L)).thenReturn(Optional.of(broadcast("IN_APP")));
        stubUsers(user(1, true), user(2, false));
        dispatcher.dispatch(5L);
        verify(notificationWriter, times(2))
                .writeInApp(anyLong(), eq(NotificationType.NEWS), any(), any(), any(), eq(5L));
        verifyNoInteractions(pushNotificationSender);
    }

    @Test
    void dispatch_botChannel_respectsPushPreference() {
        when(broadcastRepository.findById(5L)).thenReturn(Optional.of(broadcast("BOT")));
        stubUsers(user(1, true), user(2, false));
        dispatcher.dispatch(5L);
        verify(pushNotificationSender).send(eq(1001L), any(), any(), any());
        verify(pushNotificationSender, never()).send(eq(1002L), any(), any(), any());
        verifyNoInteractions(notificationWriter);
    }

    @Test
    void dispatch_marksBroadcastSentWithCount() {
        BroadcastEntity b = broadcast("IN_APP");
        when(broadcastRepository.findById(5L)).thenReturn(Optional.of(b));
        stubUsers(user(1, true), user(2, true));
        dispatcher.dispatch(5L);
        assertThat(b.getStatus()).isEqualTo(BroadcastStatus.SENT);
        assertThat(b.getSentCount()).isEqualTo(2);
        assertThat(b.getSentAt()).isNotNull();
        verify(broadcastRepository).save(b);
    }

    @Test
    void dispatch_withImage_uploadsOnceThenReusesFileId() {
        BroadcastEntity b = broadcast("BOT");
        b.setImageUrl("/api/files/broadcasts/x.png");
        when(broadcastRepository.findById(5L)).thenReturn(Optional.of(b));
        when(imageStorage.load("/api/files/broadcasts/x.png")).thenReturn(new byte[]{1, 2, 3});
        when(imageStorage.filenameOf("/api/files/broadcasts/x.png")).thenReturn("x.png");
        when(pushNotificationSender.sendPhoto(anyLong(), any(), any(), any(), any(byte[].class), any()))
                .thenReturn("FILE_ID");
        stubUsers(user(1, true), user(2, true), user(3, true));

        dispatcher.dispatch(5L);

        verify(pushNotificationSender, times(1))
                .sendPhoto(anyLong(), any(), any(), any(), any(byte[].class), any());
        verify(pushNotificationSender, times(2))
                .sendPhoto(anyLong(), any(), any(), any(), eq("FILE_ID"));
    }

    @Test
    void dispatch_withEmailTarget_loadsOnlyUsersWithEmail() {
        BroadcastEntity b = broadcast("IN_APP");
        b.setTarget(BroadcastTarget.WITH_EMAIL);
        when(broadcastRepository.findById(5L)).thenReturn(Optional.of(b));
        when(userRepository.findByEmailIsNotNullAndEmailVerifiedAtIsNotNull(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user(1, true))));

        dispatcher.dispatch(5L);

        verify(userRepository).findByEmailIsNotNullAndEmailVerifiedAtIsNotNull(any(Pageable.class));
        verify(userRepository, never()).findAll(any(Pageable.class));
    }
}
