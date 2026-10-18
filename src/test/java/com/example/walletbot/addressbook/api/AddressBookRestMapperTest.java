package com.example.walletbot.addressbook.api;

import com.example.walletbot.addressbook.api.dto.AddressBookEntryDto;
import com.example.walletbot.addressbook.domain.AddressBookDto;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AddressBookRestMapperTest {

    private final AddressBookRestMapper mapper = new AddressBookRestMapperImpl();

    @Test
    void toRestDto_mapsNetworkNameAndIsoLastUsed() {
        LocalDateTime t = LocalDateTime.of(2026, 5, 30, 12, 0);
        AddressBookDto dto = new AddressBookDto(1L, BlockchainNetwork.ETH, "0xABC", "Friend", t);

        AddressBookEntryDto rest = mapper.toRestDto(dto);

        assertThat(rest.network()).isEqualTo("ETH");
        assertThat(rest.address()).isEqualTo("0xABC");
        assertThat(rest.lastUsed()).isEqualTo(t.toString());
    }

    @Test
    void toRestDto_tonNetwork_formatsRawAddressFriendly() {
        String raw = "0:" + "0".repeat(64);
        AddressBookDto dto = new AddressBookDto(1L, BlockchainNetwork.USDT_TON, raw, null, null);

        AddressBookEntryDto rest = mapper.toRestDto(dto);

        assertThat(rest.address()).isNotEqualTo(raw);
        assertThat(rest.address()).hasSize(48);
        assertThat(rest.lastUsed()).isNull();
    }

    @Test
    void toRestDto_nonTon_leavesAddressUntouched() {
        AddressBookDto dto = new AddressBookDto(1L, BlockchainNetwork.BTC, "bc1qxyz", null, null);

        assertThat(mapper.toRestDto(dto).address()).isEqualTo("bc1qxyz");
    }
}
