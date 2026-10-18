package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.NotificationDto;
import com.example.walletbot.notification.domain.NotificationEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTest {

    private final NotificationMapper mapper = new NotificationMapperImpl();

    @Test
    void toDto_mapsDataFromDataJson() {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(3L);
        entity.setTitle("Hi");
        entity.setBody("Body text");
        entity.setDataJson("{\"k\":\"v\"}");
        entity.setRead(true);
        Instant created = Instant.now();
        entity.setCreatedAt(created);

        NotificationDto dto = mapper.toDto(entity);

        assertThat(dto.id()).isEqualTo(3L);
        assertThat(dto.title()).isEqualTo("Hi");
        assertThat(dto.body()).isEqualTo("Body text");
        assertThat(dto.data()).isEqualTo("{\"k\":\"v\"}");
        assertThat(dto.read()).isTrue();
        assertThat(dto.createdAt()).isEqualTo(created);
    }

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
