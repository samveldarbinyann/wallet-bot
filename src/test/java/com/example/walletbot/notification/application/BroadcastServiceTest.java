package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.BroadcastEntity;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastStatus;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastTarget;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastType;
import com.example.walletbot.notification.domain.BroadcastRepository;
import com.example.walletbot.notification.infrastructure.BroadcastImageStorage;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    @Mock
    BroadcastRepository broadcastRepository;
    @Mock
    BroadcastImageStorage imageStorage;
    @Mock
    BroadcastDispatcher broadcastDispatcher;
    @InjectMocks
    BroadcastService service;

    private BroadcastEntity broadcast(BroadcastStatus status) {
        BroadcastEntity b = new BroadcastEntity();
        b.setId(5L);
        b.setStatus(status);
        return b;
    }

    private BroadcastEntity captureSaved() {
        ArgumentCaptor<BroadcastEntity> captor = ArgumentCaptor.forClass(BroadcastEntity.class);
        verify(broadcastRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void create_validRequest_persistsEntity() {
        when(broadcastRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.create(9L, "NEWS", "Заголовок", "Title", "Тело", "Body",
                "WITH_EMAIL", "IN_APP", "/api/files/broadcasts/x.png");
        BroadcastEntity e = captureSaved();
        assertThat(e.getType()).isEqualTo(BroadcastType.NEWS);
        assertThat(e.getTarget()).isEqualTo(BroadcastTarget.WITH_EMAIL);
        assertThat(e.getChannels()).isEqualTo("IN_APP");
        assertThat(e.getImageUrl()).isEqualTo("/api/files/broadcasts/x.png");
    }

    @Test
    void create_nullTarget_defaultsToAll() {
        when(broadcastRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.create(9L, "APP_UPDATE", "t", "t", "b", "b", null, null, null);
        assertThat(captureSaved().getTarget()).isEqualTo(BroadcastTarget.ALL);
    }

    @Test
    void create_unknownType_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(9L, "BOGUS", "t", "t", "b", "b", null, null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_unknownTarget_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(9L, "NEWS", "t", "t", "b", "b", "PLANET", null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void send_draftBroadcast_marksSendingAndDispatches() {
        BroadcastEntity b = broadcast(BroadcastStatus.DRAFT);
        when(broadcastRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(b));
        service.send(5L);
        assertThat(b.getStatus()).isEqualTo(BroadcastStatus.SENDING);
        verify(broadcastRepository).save(b);
        verify(broadcastDispatcher).dispatch(5L);
    }

    @Test
    void send_alreadySending_doesNotDispatchAgain() {
        when(broadcastRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.of(broadcast(BroadcastStatus.SENDING)));
        service.send(5L);
        verify(broadcastDispatcher, never()).dispatch(any());
    }

    @Test
    void send_alreadySent_doesNotDispatchAgain() {
        when(broadcastRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.of(broadcast(BroadcastStatus.SENT)));
        service.send(5L);
        verify(broadcastDispatcher, never()).dispatch(any());
    }

    @Test
    void send_unknownBroadcast_throwsNotFound() {
        when(broadcastRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.send(5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void uploadImage_delegatesToStorage() {
        MultipartFile file = mock(MultipartFile.class);
        when(imageStorage.save(file)).thenReturn("/api/files/broadcasts/y.png");
        assertThat(service.uploadImage(file)).isEqualTo("/api/files/broadcasts/y.png");
    }
}
