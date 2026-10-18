package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.NotificationEntity;
import com.example.walletbot.notification.domain.NotificationRepository;
import com.example.walletbot.shared.constant.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationWriterTest {

    @Mock
    NotificationRepository notificationRepository;
    @InjectMocks
    NotificationWriter writer;

    private NotificationEntity captureSaved() {
        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void writeInApp_nullData_leavesDataJsonNull() {
        writer.writeInApp(1L, NotificationType.DEPOSIT, "t", "b", null);
        assertThat(captureSaved().getDataJson()).isNull();
    }

    @Test
    void writeInApp_emptyData_leavesDataJsonNull() {
        writer.writeInApp(1L, NotificationType.DEPOSIT, "t", "b", Map.of());
        assertThat(captureSaved().getDataJson()).isNull();
    }

    @Test
    void writeInApp_withData_serializesJson() {
        writer.writeInApp(1L, NotificationType.NEWS, "t", "b",
                Map.of("image", "/api/files/broadcasts/x.png"));
        assertThat(captureSaved().getDataJson())
                .isEqualTo("{\"image\":\"/api/files/broadcasts/x.png\"}");
    }

    @Test
    void writeInApp_setsAllFields() {
        writer.writeInApp(7L, NotificationType.SWAP, "Title", "Body", null);
        NotificationEntity n = captureSaved();
        assertThat(n.getUserId()).isEqualTo(7L);
        assertThat(n.getType()).isEqualTo(NotificationType.SWAP);
        assertThat(n.getTitle()).isEqualTo("Title");
        assertThat(n.getBody()).isEqualTo("Body");
    }

    @Test
    void writeInApp_truncatesOverlongTitleAndBody() {
        writer.writeInApp(1L, NotificationType.NEWS, "T".repeat(300), "B".repeat(2000), null);
        NotificationEntity n = captureSaved();
        assertThat(n.getTitle()).hasSize(200);
        assertThat(n.getBody()).hasSize(1000);
    }
}
