package com.example.walletbot.platform.blockchain.token;

import com.example.walletbot.platform.blockchain.BlockchainService.TxStatusResult;
import com.example.walletbot.platform.blockchain.BlockchainService.WalletKeys;
import com.example.walletbot.platform.blockchain.JettonWalletResolver;
import com.example.walletbot.platform.blockchain.chain.TonBlockchainService;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Timeout(30)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TonTokenServiceTest {

    private static final int WALLET_ID = 698983191;

    private static String from;
    private static String mnemonic;

    private TonBlockchainService tonService;
    private TonCenterClient tonCenter;
    private JettonWalletResolver resolver;
    private TonTokenService service;

    @BeforeAll
    static void generateKeys() {
        WalletKeys keys = new TonBlockchainService(mock(TonCenterClient.class), WALLET_ID, "comment", 60L)
                .generateWallet();
        from = keys.address();
        mnemonic = keys.privateKey();
    }

    @BeforeEach
    void setUp() {
        tonService = mock(TonBlockchainService.class);
        tonCenter = mock(TonCenterClient.class);
        resolver = mock(JettonWalletResolver.class);
        service = new TonTokenService(BlockchainNetwork.USDT_TON, tonService, tonCenter, resolver, WALLET_ID);
    }

    @Test
    void getNetwork_isUsdtTon() {
        assertThat(service.getNetwork()).isEqualTo(BlockchainNetwork.USDT_TON);
    }

    @Test
    void estimateSendFee_isJettonGasBudget() {
        assertThat(service.estimateSendFee()).isEqualByComparingTo("0.05");
    }

    @Test
    void getFeeSymbol_isTonNativeSymbol() {
        assertThat(service.getFeeSymbol()).isEqualTo(BlockchainNetwork.TON.getSymbol());
    }

    @Test
    void generateWallet_delegatesToChainService() {
        WalletKeys keys = new WalletKeys("EQaddr", "phrase");
        when(tonService.generateWallet()).thenReturn(keys);
        assertThat(service.generateWallet()).isSameAs(keys);
    }

    @Test
    void deriveAddress_delegatesToChainService() {
        when(tonService.deriveAddress("phrase")).thenReturn("EQderived");
        assertThat(service.deriveAddress("phrase")).isEqualTo("EQderived");
    }

    @Test
    void isValidAddress_delegatesToChainService() {
        when(tonService.isValidAddress("EQx")).thenReturn(true);
        assertThat(service.isValidAddress("EQx")).isTrue();
    }

    @Test
    void getTxStatus_delegatesToChainService() {
        TxStatusResult pending = TxStatusResult.pending();
        when(tonService.getTxStatus("hash", "EQx")).thenReturn(pending);
        assertThat(service.getTxStatus("hash", "EQx")).isSameAs(pending);
    }

    @Test
    void getBalance_decodesNanoJettonToSixDecimals() {
        when(resolver.resolve("EQowner")).thenReturn("EQjetton");
        when(tonCenter.runGetMethodForBalance("EQjetton")).thenReturn(BigInteger.valueOf(123_456_789L));

        assertThat(service.getBalance("EQowner")).isEqualTo("123.456789");
    }

    @Test
    void getBalance_noJettonWallet_returnsZero() {
        when(resolver.resolve("EQowner")).thenReturn(null);
        assertThat(service.getBalance("EQowner")).isEqualTo("0.000000");
    }

    @Test
    void getBalance_nullRawBalance_returnsZero() {
        when(resolver.resolve("EQowner")).thenReturn("EQjetton");
        when(tonCenter.runGetMethodForBalance("EQjetton")).thenReturn(null);
        assertThat(service.getBalance("EQowner")).isEqualTo("0.000000");
    }

    @Test
    void getBalance_rpcThrows_returnsZero() {
        when(resolver.resolve("EQowner")).thenReturn("EQjetton");
        when(tonCenter.runGetMethodForBalance("EQjetton")).thenThrow(new RuntimeException("rpc"));
        assertThat(service.getBalance("EQowner")).isEqualTo("0.000000");
    }

    @Test
    void send_wrongWordCount_throwsBadRequest() {
        assertThatThrownBy(() -> service.send("too few words", from, new BigDecimal("10")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void send_jettonWalletUnresolvable_throwsRpc() {
        when(resolver.resolve(from)).thenReturn(null);

        assertThatThrownBy(() -> service.send(mnemonic, from, new BigDecimal("10")))
                .isInstanceOf(BlockchainRpcException.class)
                .hasMessageContaining("jetton wallet");
    }

    @Test
    void send_insufficientUsdt_throws() {
        when(resolver.resolve(any())).thenReturn(from);
        when(tonCenter.runGetMethodForBalance(from)).thenReturn(BigInteger.valueOf(5_000_000L));

        assertThatThrownBy(() -> service.send(mnemonic, from, new BigDecimal("10")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("USDT");
    }

    @Test
    void send_insufficientTonForGas_throws() {
        when(resolver.resolve(any())).thenReturn(from);
        when(tonCenter.runGetMethodForBalance(from)).thenReturn(BigInteger.valueOf(100_000_000L));
        when(tonService.getBalance(from)).thenReturn("0.01");

        assertThatThrownBy(() -> service.send(mnemonic, from, new BigDecimal("10")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("gas");
    }

    @Test
    void send_happyPath_broadcastsAndInvalidatesCache() {
        when(resolver.resolve(any())).thenReturn(from);
        when(tonCenter.runGetMethodForBalance(from)).thenReturn(BigInteger.valueOf(100_000_000L));
        when(tonService.getBalance(from)).thenReturn("1.0");
        when(tonService.resolveSeqno(from)).thenReturn(5L);
        when(tonCenter.sendBocReturnHash(anyString())).thenReturn("jettonHash");

        assertThat(service.send(mnemonic, from, new BigDecimal("10"))).isEqualTo("jettonHash");
        verify(tonCenter).sendBocReturnHash(anyString());
        verify(resolver).invalidate(from);
    }

    @Test
    void send_uninitializedAccount_attachesStateInitAndSucceeds() {
        when(resolver.resolve(any())).thenReturn(from);
        when(tonCenter.runGetMethodForBalance(from)).thenReturn(BigInteger.valueOf(100_000_000L));
        when(tonService.getBalance(from)).thenReturn("1.0");
        when(tonService.resolveSeqno(from)).thenReturn(0L);
        when(tonCenter.sendBocReturnHash(anyString())).thenReturn("deployHash");

        assertThat(service.send(mnemonic, from, new BigDecimal("10"))).isEqualTo("deployHash");
    }
}
