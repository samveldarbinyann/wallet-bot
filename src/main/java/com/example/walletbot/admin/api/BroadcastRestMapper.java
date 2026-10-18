package com.example.walletbot.admin.api;

import com.example.walletbot.notification.domain.BroadcastDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BroadcastRestMapper {

    @Mapping(target = "type", expression = "java(dto.type().name())")
    @Mapping(target = "target", expression = "java(dto.target().name())")
    @Mapping(target = "status", expression = "java(dto.status().name())")
    @Mapping(target = "createdAt", expression = "java(dto.createdAt() != null ? dto.createdAt().toString() : null)")
    @Mapping(target = "sentAt", expression = "java(dto.sentAt() != null ? dto.sentAt().toString() : null)")
    com.example.walletbot.admin.api.dto.BroadcastDto toRestDto(BroadcastDto dto);
}
