package com.example.walletbot.platform.blockchain.token;

import com.example.walletbot.platform.blockchain.BlockchainService.TxStatusResult;
import com.example.walletbot.platform.blockchain.BlockchainService.WalletKeys;
import com.example.walletbot.platform.blockchain.chain.EvmBlockchainService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.TokenInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErcTokenServiceTest {

    private static final String OVERRIDE_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
    private static final String ADDR = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String PK = "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";

    private static final String BEP20_CONTRACT = "0x55d398326f99059fF775485246999027B3197955";

    private EvmBlockchainService evm;
    private ErcTokenService withToken;
    private ErcTokenService withOverride;
    private ErcTokenService bep20;

    @BeforeEach
    void setUp() {
        evm = mock(EvmBlockchainService.class);
        withToken = new ErcTokenService(BlockchainNetwork.USDT_ERC20, TokenInfo.USDT, evm, null);
        withOverride = new ErcTokenService(BlockchainNetwork.USDT_ERC20, TokenInfo.USDT, evm, OVERRIDE_CONTRACT);
        bep20 = new ErcTokenService(BlockchainNetwork.USDT_BEP20, TokenInfo.USDT, evm, BEP20_CONTRACT);
    }

    @Test
    void getBalance_bep20_usesEighteenDecimals() {
        when(evm.getTokenBalance(ADDR, BEP20_CONTRACT, 18)).thenReturn("9.0");
        assertThat(bep20.getBalance(ADDR)).isEqualTo("9.0");
        verify(evm).getTokenBalance(ADDR, BEP20_CONTRACT, 18);
        verifyNoMoreInteractions(evm);
    }

    @Test
    void send_bep20_usesEighteenDecimals() {
        BigDecimal amount = new BigDecimal("3");
        when(evm.sendToken(PK, ADDR, amount, BEP20_CONTRACT, 18)).thenReturn("0xhash3");
        assertThat(bep20.send(PK, ADDR, amount)).isEqualTo("0xhash3");
        verify(evm).sendToken(PK, ADDR, amount, BEP20_CONTRACT, 18);
    }

    @Test
    void getNetwork_returnsConfiguredNetwork() {
        assertThat(withToken.getNetwork()).isEqualTo(BlockchainNetwork.USDT_ERC20);
    }

    @Test
    void getBalance_noOverride_usesTokenAndNetworkOverload() {
        when(evm.getTokenBalance(ADDR, TokenInfo.USDT, BlockchainNetwork.USDT_ERC20)).thenReturn("12.5");
        assertThat(withToken.getBalance(ADDR)).isEqualTo("12.5");
        verify(evm).getTokenBalance(ADDR, TokenInfo.USDT, BlockchainNetwork.USDT_ERC20);
        verifyNoMoreInteractions(evm);
    }

    @Test
    void getBalance_withOverride_usesRawContractOverload() {
        when(evm.getTokenBalance(ADDR, OVERRIDE_CONTRACT, 6)).thenReturn("7.0");
        assertThat(withOverride.getBalance(ADDR)).isEqualTo("7.0");
        verify(evm).getTokenBalance(ADDR, OVERRIDE_CONTRACT, 6);
        verifyNoMoreInteractions(evm);
    }

    @Test
    void send_noOverride_usesTokenAndNetworkOverload() {
        BigDecimal amount = new BigDecimal("3");
        when(evm.sendToken(PK, ADDR, amount, TokenInfo.USDT, BlockchainNetwork.USDT_ERC20)).thenReturn("0xhash");
        assertThat(withToken.send(PK, ADDR, amount)).isEqualTo("0xhash");
        verify(evm).sendToken(PK, ADDR, amount, TokenInfo.USDT, BlockchainNetwork.USDT_ERC20);
    }

    @Test
    void send_withOverride_usesRawContractOverload() {
        BigDecimal amount = new BigDecimal("3");
        when(evm.sendToken(PK, ADDR, amount, OVERRIDE_CONTRACT, 6)).thenReturn("0xhash2");
        assertThat(withOverride.send(PK, ADDR, amount)).isEqualTo("0xhash2");
        verify(evm).sendToken(PK, ADDR, amount, OVERRIDE_CONTRACT, 6);
    }

    @Test
    void estimateSendFee_noOverride_usesTokenAndNetworkOverload() {
        when(evm.estimateTokenGasCost(TokenInfo.USDT, BlockchainNetwork.USDT_ERC20))
                .thenReturn(new BigDecimal("0.0021"));
        assertThat(withToken.estimateSendFee()).isEqualByComparingTo("0.0021");
    }

    @Test
    void estimateSendFee_withOverride_usesRawContractOverload() {
        when(evm.estimateTokenGasCost(OVERRIDE_CONTRACT)).thenReturn(new BigDecimal("0.0042"));
        assertThat(withOverride.estimateSendFee()).isEqualByComparingTo("0.0042");
    }

    @Test
    void generateWallet_delegates() {
        WalletKeys keys = new WalletKeys("0xabc", "pk");
        when(evm.generateWallet()).thenReturn(keys);
        assertThat(withToken.generateWallet()).isSameAs(keys);
    }

    @Test
    void deriveAddress_delegates() {
        when(evm.deriveAddress(PK)).thenReturn(ADDR);
        assertThat(withToken.deriveAddress(PK)).isEqualTo(ADDR);
    }

    @Test
    void isValidAddress_delegates() {
        when(evm.isValidAddress(ADDR)).thenReturn(true);
        assertThat(withToken.isValidAddress(ADDR)).isTrue();
    }

    @Test
    void getTxStatus_delegates() {
        TxStatusResult pending = TxStatusResult.pending();
        when(evm.getTxStatus("0xtx", ADDR)).thenReturn(pending);
        assertThat(withToken.getTxStatus("0xtx", ADDR)).isSameAs(pending);
    }

    @Test
    void getFeeSymbol_isParentNativeSymbol() {
        when(evm.getNetwork()).thenReturn(BlockchainNetwork.ETH);
        assertThat(withToken.getFeeSymbol()).isEqualTo(BlockchainNetwork.ETH.getSymbol());
    }
}
