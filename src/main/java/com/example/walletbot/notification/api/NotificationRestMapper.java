package com.example.walletbot.notification.api;

import com.example.walletbot.notification.domain.NotificationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationRestMapper {

    @Mapping(target = "id", expression = "java(dto.id().toString())")
    @Mapping(target = "type", expression = "java(dto.type().name())")
    @Mapping(target = "createdAt", expression = "java(dto.createdAt().toString())")
    com.example.walletbot.notification.api.dto.NotificationDto toRestDto(NotificationDto dto);
}
