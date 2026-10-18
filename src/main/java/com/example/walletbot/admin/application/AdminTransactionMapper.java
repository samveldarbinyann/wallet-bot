package com.example.walletbot.admin.application;

import com.example.walletbot.admin.domain.AdminTransactionDto;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminTransactionMapper {
    AdminTransactionDto toDto(TransactionLogEntity entity);
}

