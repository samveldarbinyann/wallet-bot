package com.example.walletbot.admin.application;

import com.example.walletbot.notification.application.BroadcastMapper;
import com.example.walletbot.notification.application.BroadcastService;
import com.example.walletbot.notification.domain.BroadcastDto;
import com.example.walletbot.notification.domain.BroadcastEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBroadcastServiceTest {

    @Mock
    BroadcastService broadcastService;
    @Mock
    BroadcastMapper broadcastMapper;

    @InjectMocks
    AdminBroadcastService service;

    @Test
    void create_delegates() {
        BroadcastEntity entity = new BroadcastEntity();
        entity.setId(7L);
        BroadcastDto dto = new BroadcastDto(7L, 1L, null, null, null, null, null, null, null, null, null, 0, null, null);
        when(broadcastService.create(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(entity);
        when(broadcastMapper.toDto(entity)).thenReturn(dto);

        BroadcastDto result = service.create(1L, "NEWS", "тР", "tE", "бР", "bE", "ALL", "PUSH", null);

        assertThat(result).isSameAs(dto);
        verify(broadcastService).create(1L, "NEWS", "тР", "tE", "бР", "bE", "ALL", "PUSH", null);
    }

    @Test
    void send_delegates() {
        BroadcastEntity entity = new BroadcastEntity();
        BroadcastDto dto = new BroadcastDto(9L, 1L, null, null, null, null, null, null, null, null, null, 0, null, null);
        when(broadcastService.send(9L)).thenReturn(entity);
        when(broadcastMapper.toDto(entity)).thenReturn(dto);

        BroadcastDto result = service.send(9L);

        assertThat(result).isSameAs(dto);
        verify(broadcastService).send(9L);
    }

    @Test
    void delete_delegates() {
        service.delete(9L);

        verify(broadcastService).delete(9L);
    }
}
