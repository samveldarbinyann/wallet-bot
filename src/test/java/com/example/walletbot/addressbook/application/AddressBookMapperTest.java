package com.example.walletbot.addressbook.application;

import com.example.walletbot.addressbook.domain.AddressBookDto;
import com.example.walletbot.addressbook.domain.AddressBookEntity;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AddressBookMapperTest {

    private final AddressBookMapper mapper = new AddressBookMapperImpl();

    @Test
    void toDto_mapsAllExposedFields() {
        AddressBookEntity entity = new AddressBookEntity();
        entity.setId(7L);
        entity.setUserId(1L);
        entity.setNetwork(BlockchainNetwork.ETH);
        entity.setAddress("0xABC");
        entity.setLabel("Friend");
        LocalDateTime lastUsed = LocalDateTime.now();
        entity.setLastUsed(lastUsed);

        AddressBookDto dto = mapper.toDto(entity);

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.network()).isEqualTo(BlockchainNetwork.ETH);
        assertThat(dto.address()).isEqualTo("0xABC");
        assertThat(dto.label()).isEqualTo("Friend");
        assertThat(dto.lastUsed()).isEqualTo(lastUsed);
    }

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
