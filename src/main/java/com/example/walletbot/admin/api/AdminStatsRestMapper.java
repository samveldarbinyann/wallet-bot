package com.example.walletbot.admin.api;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminStatsRestMapper {

    com.example.walletbot.admin.api.dto.AdminStatsDto toRestDto(
            com.example.walletbot.admin.domain.AdminStatsDto dto);
}
