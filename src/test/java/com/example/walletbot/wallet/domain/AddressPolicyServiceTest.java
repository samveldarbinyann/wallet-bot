package com.example.walletbot.wallet.domain;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressPolicyServiceTest {

    @Mock
    BlockchainServiceFactory factory;
    @Mock
    BlockchainService blockchainService;

    @InjectMocks
    AddressPolicyService service;

    @Test
    void requireValid_nullAddress_throws() {
        assertThatThrownBy(() -> service.requireValid(BlockchainNetwork.ETH, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.address_required");
    }

    @Test
    void requireValid_blankAddress_throws() {
        assertThatThrownBy(() -> service.requireValid(BlockchainNetwork.ETH, "   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.address_required");
    }

    @Test
    void requireValid_invalidAddress_throws() {
        when(factory.getService(BlockchainNetwork.ETH)).thenReturn(blockchainService);
        when(blockchainService.isValidAddress("0xbad")).thenReturn(false);

        assertThatThrownBy(() -> service.requireValid(BlockchainNetwork.ETH, "0xbad"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.invalid_address_for_network");
    }

    @Test
    void requireValid_validAddress_trimsAndPasses() {
        when(factory.getService(BlockchainNetwork.ETH)).thenReturn(blockchainService);
        when(blockchainService.isValidAddress("0xgood")).thenReturn(true);

        assertThatCode(() -> service.requireValid(BlockchainNetwork.ETH, "  0xgood  "))
                .doesNotThrowAnyException();
    }
}
