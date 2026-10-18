package com.example.walletbot.admin.api;

import com.example.walletbot.admin.api.dto.AdminTransactionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminTransactionRestMapper {

    @Mapping(target = "type", expression = "java(dto.type().name())")
    @Mapping(target = "network", expression = "java(dto.network() != null ? dto.network().name() : null)")
    @Mapping(target = "status", expression = "java(dto.status().name())")
    @Mapping(target = "createdAt", expression = "java(dto.createdAt() != null ? dto.createdAt().atOffset(java.time.ZoneOffset.UTC).toString() : null)")
    @Mapping(target = "txTimestamp", expression = "java(dto.txTimestamp() != null ? dto.txTimestamp().atOffset(java.time.ZoneOffset.UTC).toString() : null)")
    AdminTransactionDto toRestDto(com.example.walletbot.admin.domain.AdminTransactionDto dto);
}
