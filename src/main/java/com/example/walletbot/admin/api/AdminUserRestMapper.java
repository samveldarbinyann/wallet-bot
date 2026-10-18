package com.example.walletbot.admin.api;

import com.example.walletbot.admin.api.dto.AdminUserListItemDto;
import com.example.walletbot.admin.domain.AdminUserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminUserRestMapper {

    @Mapping(target = "email", source = "maskedEmail")
    @Mapping(target = "role", expression = "java(dto.role() != null ? dto.role().name() : \"USER\")")
    @Mapping(target = "status", expression = "java(dto.status() != null ? dto.status().name() : \"ACTIVE\")")
    @Mapping(target = "createdAt", expression = "java(dto.createdAt() != null ? dto.createdAt().toString() : null)")
    @Mapping(target = "lastLoginAt", expression = "java(dto.lastLoginAt() != null ? dto.lastLoginAt().toString() : null)")
    AdminUserListItemDto toRestDto(AdminUserDto dto);
}