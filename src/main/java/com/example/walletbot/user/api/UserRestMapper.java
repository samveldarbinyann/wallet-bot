package com.example.walletbot.user.api;

import com.example.walletbot.user.api.dto.UserResponse;
import com.example.walletbot.user.domain.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserRestMapper {

    @Mapping(target = "id", expression = "java(dto.id().toString())")
    @Mapping(target = "telegramId", expression = "java(dto.telegramId().toString())")
    @Mapping(target = "createdAt", expression = "java(dto.createdAt() != null ? dto.createdAt().toString() : null)")
    @Mapping(target = "email", source = "maskedEmail")
    @Mapping(target = "role", expression = "java(dto.role() != null ? dto.role().name() : \"USER\")")
    @Mapping(target = "status", expression = "java(dto.status() != null ? dto.status().name() : \"ACTIVE\")")
    UserResponse toRestDto(UserDto dto);
}
