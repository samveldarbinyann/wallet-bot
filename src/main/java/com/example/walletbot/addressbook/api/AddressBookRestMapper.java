package com.example.walletbot.addressbook.api;

import com.example.walletbot.addressbook.api.dto.AddressBookEntryDto;
import com.example.walletbot.addressbook.domain.AddressBookDto;
import com.example.walletbot.platform.blockchain.util.TonAddressFormatter;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AddressBookRestMapper {

    @Mapping(target = "network", expression = "java(dto.network().name())")
    @Mapping(target = "address", expression = "java(mapAddress(dto))")
    @Mapping(target = "lastUsed", expression = "java(dto.lastUsed() != null ? dto.lastUsed().toString() : null)")
    AddressBookEntryDto toRestDto(AddressBookDto dto);

    default String mapAddress(AddressBookDto dto) {
        if (dto.network().getGasNetwork() == BlockchainNetwork.TON) {
            return TonAddressFormatter.toFriendly(dto.address());
        }
        return dto.address();
    }
}