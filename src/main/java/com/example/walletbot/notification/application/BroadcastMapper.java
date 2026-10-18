package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.BroadcastDto;
import com.example.walletbot.notification.domain.BroadcastEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BroadcastMapper {
    BroadcastDto toDto(BroadcastEntity entity);
}
