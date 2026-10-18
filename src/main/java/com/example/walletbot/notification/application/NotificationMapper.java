package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.NotificationDto;
import com.example.walletbot.notification.domain.NotificationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    @Mapping(target = "data", source = "dataJson")
    NotificationDto toDto(NotificationEntity entity);
}
