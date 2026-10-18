package com.example.walletbot.transaction.application;

import com.example.walletbot.platform.blockchain.util.TonAddressFormatter;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.domain.TransactionDto;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface TransactionLogMapper {
    @Mapping(target = "id", expression = "java(entity.getId().toString())")
    @Mapping(target = "type", expression = "java(mapType(entity.getType()))")
    @Mapping(target = "status", expression = "java(mapStatus(entity.getStatus()))")
    @Mapping(target = "network", expression = "java(entity.getNetwork() != null ? entity.getNetwork().name() : null)")
    @Mapping(target = "address", expression = "java(mapAddress(entity))")
    @Mapping(target = "createdAt", expression = "java(entity.getDisplayTime().atOffset(java.time.ZoneOffset.UTC).toString())")
    @Mapping(target = "usdValue", source = "usdValue")
    TransactionDto toDto(TransactionLogEntity entity, BigDecimal usdValue);

    default String mapAddress(TransactionLogEntity entity) {
        String addr = entity.getType() == TransactionLogEntity.TxType.DEPOSIT
                ? entity.getFromAddress() : entity.getToAddress();
        BlockchainNetwork net = entity.getNetwork();
        if (net != null && net.getGasNetwork() == BlockchainNetwork.TON) {
            return TonAddressFormatter.toFriendly(addr);
        }
        return addr;
    }

    default String mapType(TransactionLogEntity.TxType type) {
        return switch (type) {
            case DEPOSIT -> "IN";
            case SEND, SWAP -> "OUT";
        };
    }

    default String mapStatus(TransactionLogEntity.TxStatus status) {
        return switch (status) {
            case SUCCESS -> "COMPLETED";
            case PENDING -> "PENDING";
            case FAILED -> "FAILED";
        };
    }
}