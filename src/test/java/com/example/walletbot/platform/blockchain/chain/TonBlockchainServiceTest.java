package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.platform.blockchain.BlockchainService.OnChainStatus;
import com.example.walletbot.platform.blockchain.BlockchainService.TxStatusResult;
import com.example.walletbot.platform.blockchain.BlockchainService.WalletKeys;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.platform.blockchain.client.TonCenterClient.TonTxInfo;
import com.example.walletbot.platform.blockchain.client.TonCenterClient.WalletInformation;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Timeout(30)
class TonBlockchainServiceTest {

    private static final int WALLET_ID = 698983191;

    private static WalletKeys keys;
    private static String from;
    private static String mnemonic;

    private TonCenterClient tonCenter;
    private TonBlockchainService service;

    @BeforeAll
    static void generateKeys() {
        keys = new TonBlockchainService(mock(TonCenterClient.class), WALLET_ID, "comment", 60L)
                .generateWallet();
        from = keys.address();
        mnemonic = keys.privateKey();
    }

    @BeforeEach
    void setUp() {
        tonCenter = mock(TonCenterClient.class);
        service = new TonBlockchainService(tonCenter, WALLET_ID, "comment", 60L);
    }

    @Test
    void isValidAddress_rawBasechainAddress_valid() {
        assertThat(service.isValidAddress("0:" + "0".repeat(64))).isTrue();
    }

    @Test
    void isValidAddress_nonBasechainWorkchain_invalid() {
        assertThat(service.isValidAddress("-1:" + "0".repeat(64))).isFalse();
    }

    @Test
    void isValidAddress_nullOrBlank_invalid() {
        assertThat(service.isValidAddress(null)).isFalse();
        assertThat(service.isValidAddress("  ")).isFalse();
    }

    @Test
    void isValidAddress_malformedInput_returnsFalseNotError() {
        assertThat(service.isValidAddress("not-a-ton-address")).isFalse();
        assertThat(service.isValidAddress("kQabc")).isFalse();
        assertThat(service.isValidAddress("0:xyz")).isFalse();
    }

    @Test
    void generateWallet_producesValidBasechainAddressAnd24WordMnemonic() {
        assertThat(service.isValidAddress(from)).isTrue();
        assertThat(mnemonic.split(" ")).hasSize(24);
    }

    @Test
    void deriveAddress_roundTripsGeneratedMnemonic() {
        assertThat(service.deriveAddress(mnemonic)).isEqualTo(from);
    }

    @Test
    void deriveAddress_nullMnemonic_throwsBadRequest() {
        assertThatThrownBy(() -> service.deriveAddress(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getBalance_formatsToNineDecimals() {
        when(tonCenter.getBalanceTon(from)).thenReturn(new BigDecimal("1.23"));
        assertThat(service.getBalance(from)).isEqualTo("1.230000000");
    }

    @Test
    void estimateSendFee_isFixedNetworkFee() {
        assertThat(service.estimateSendFee()).isEqualByComparingTo("0.01");
    }

    @Test
    void resolveSeqno_uninitializedAccount_returnsZero() {
        when(tonCenter.getWalletInformation(from)).thenReturn(new WalletInformation("uninitialized", null));
        assertThat(service.resolveSeqno(from)).isZero();
    }

    @Test
    void resolveSeqno_activeAccount_returnsSeqno() {
        when(tonCenter.getWalletInformation(from)).thenReturn(new WalletInformation("active", 7L));
        assertThat(service.resolveSeqno(from)).isEqualTo(7L);
    }

    @Test
    void resolveSeqno_activeButNoSeqno_throwsRpc() {
        when(tonCenter.getWalletInformation(from)).thenReturn(new WalletInformation("active", null));
        assertThatThrownBy(() -> service.resolveSeqno(from)).isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void getTxStatus_nullFromAddress_pending() {
        assertThat(service.getTxStatus("hash", null).status()).isEqualTo(OnChainStatus.PENDING);
    }

    @Test
    void getTxStatus_notFoundInHistory_pending() {
        when(tonCenter.findTransactionByInMessageHash(from, "hash")).thenReturn(null);
        assertThat(service.getTxStatus("hash", from).status()).isEqualTo(OnChainStatus.PENDING);
    }

    @Test
    void getTxStatus_found_confirmedWithTimestampAndFee() {
        when(tonCenter.findTransactionByInMessageHash(from, "hash"))
                .thenReturn(new TonTxInfo(1_700_000_000L, new BigDecimal("0.0123")));

        TxStatusResult result = service.getTxStatus("hash", from);

        assertThat(result.status()).isEqualTo(OnChainStatus.CONFIRMED);
        assertThat(result.blockTimestamp()).isNotNull();
        assertThat(result.feeNative()).isEqualByComparingTo("0.0123");
    }

    @Test
    void getTxStatus_rpcThrows_pending() {
        when(tonCenter.findTransactionByInMessageHash(from, "hash")).thenThrow(new RuntimeException("rpc"));
        assertThat(service.getTxStatus("hash", from).status()).isEqualTo(OnChainStatus.PENDING);
    }

    @Test
    void send_wrongWordCount_throwsBadRequest() {
        assertThatThrownBy(() -> service.send("only three words", from, new BigDecimal("0.5")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void send_insufficientBalanceForAmountPlusFee_throws() {
        when(tonCenter.getBalanceTon(from)).thenReturn(new BigDecimal("0.2"));
        assertThatThrownBy(() -> service.send(mnemonic, from, new BigDecimal("0.5")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void send_activeAccount_signsAndReturnsHash() {
        when(tonCenter.getBalanceTon(from)).thenReturn(new BigDecimal("2.0"));
        when(tonCenter.getWalletInformation(from)).thenReturn(new WalletInformation("active", 5L));
        when(tonCenter.sendBocReturnHash(anyString())).thenReturn("tonHash");

        assertThat(service.send(mnemonic, from, new BigDecimal("0.5"))).isEqualTo("tonHash");
        verify(tonCenter).sendBocReturnHash(anyString());
    }

    @Test
    void send_uninitializedAccount_attachesStateInitAndSucceeds() {
        when(tonCenter.getBalanceTon(from)).thenReturn(new BigDecimal("2.0"));
        when(tonCenter.getWalletInformation(from)).thenReturn(new WalletInformation("uninitialized", null));
        when(tonCenter.sendBocReturnHash(anyString())).thenReturn("deployHash");

        assertThat(service.send(mnemonic, from, new BigDecimal("0.5"))).isEqualTo("deployHash");
    }

    @Test
    void sendWithMemo_signsAndReturnsHash() {
        when(tonCenter.getBalanceTon(from)).thenReturn(new BigDecimal("2.0"));
        when(tonCenter.getWalletInformation(from)).thenReturn(new WalletInformation("active", 5L));
        when(tonCenter.sendBocReturnHash(anyString())).thenReturn("memoHash");

        assertThat(service.sendWithMemo(mnemonic, from, new BigDecimal("0.5"), "swap-deposit-123"))
                .isEqualTo("memoHash");
    }

    @Test
    void send_bounceOverride_signsAndReturnsHash() {
        when(tonCenter.getBalanceTon(from)).thenReturn(new BigDecimal("2.0"));
        when(tonCenter.getWalletInformation(from)).thenReturn(new WalletInformation("active", 5L));
        when(tonCenter.sendBocReturnHash(anyString())).thenReturn("bounceHash");

        assertThat(service.send(mnemonic, from, new BigDecimal("0.5"), Boolean.FALSE)).isEqualTo("bounceHash");
    }

    @Test
    void send_seqnoLookupFails_wrappedAsRpc() {
        when(tonCenter.getBalanceTon(from)).thenReturn(new BigDecimal("2.0"));
        when(tonCenter.getWalletInformation(from)).thenThrow(new RuntimeException("ton down"));

        assertThatThrownBy(() -> service.send(mnemonic, from, new BigDecimal("0.5"), null))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void send_broadcastFails_wrappedAsRpc() {
        when(tonCenter.getBalanceTon(from)).thenReturn(new BigDecimal("2.0"));
        when(tonCenter.getWalletInformation(from)).thenReturn(new WalletInformation("active", 5L));
        when(tonCenter.sendBocReturnHash(anyString())).thenThrow(new BlockchainRpcException("rejected"));

        assertThatThrownBy(() -> service.send(mnemonic, from, new BigDecimal("0.5"), null))
                .isInstanceOf(BlockchainRpcException.class);
    }
}
