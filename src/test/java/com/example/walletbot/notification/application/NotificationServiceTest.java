package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.NotificationRepository;
import com.example.walletbot.shared.constant.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;
    @Mock
    NotificationDispatcher notificationDispatcher;
    @InjectMocks
    NotificationService service;

    @Test
    void send_withNoActiveTransaction_dispatchesImmediately() {
        service.send(1L, NotificationType.DEPOSIT, null, "t", "b", "x");

        verify(notificationDispatcher).deliver(1L, NotificationType.DEPOSIT, null, "t", "b", "x");
    }

    @Test
    void getUserNotifications_clampsSizeToAtLeastOne() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getUserNotifications(1L, -3, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void getUserNotifications_clampsSizeToFifty() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getUserNotifications(1L, 2, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void getUnreadCount_delegatesToRepository() {
        when(notificationRepository.countByUserIdAndReadFalse(1L)).thenReturn(7L);

        assertThat(service.getUnreadCount(1L)).isEqualTo(7L);
    }

    @Test
    void markAllRead_delegatesToRepository() {
        service.markAllRead(1L);

        verify(notificationRepository).markAllAsRead(1L);
    }
}