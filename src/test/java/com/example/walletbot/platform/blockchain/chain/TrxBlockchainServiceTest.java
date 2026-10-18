package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.platform.blockchain.BlockchainService.OnChainStatus;
import com.example.walletbot.platform.blockchain.BlockchainService.TxStatusResult;
import com.example.walletbot.platform.blockchain.BlockchainService.WalletKeys;
import com.example.walletbot.platform.blockchain.cache.TronAccountInFlightCache;
import com.example.walletbot.platform.blockchain.chain.TrxBlockchainService.AccountResources;
import com.example.walletbot.platform.blockchain.client.TronGridClient;
import com.example.walletbot.platform.blockchain.util.TronCryptoUtils;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrxBlockchainServiceTest {

    private static final String PK_FROM = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String FROM = TronCryptoUtils.getAddressFromPrivateKey(PK_FROM);
    private static final String TO = TronCryptoUtils.getAddressFromPrivateKey(
            "0000000000000000000000000000000000000000000000000000000000000002");
    private static final String TXID = "a1b2c3d4".repeat(8);

    private final TronGridClient tronGrid = mock(TronGridClient.class);
    private final TrxBlockchainService service = new TrxBlockchainService(
            tronGrid,
            new TronAccountInFlightCache());

    private static Map<String, Object> account(long trxSun) {
        return Map.of("data", List.of(Map.of("balance", trxSun)));
    }

    @Test
    void isValidAddress_realMainnetAddress_valid() {
        String addr = TronCryptoUtils.getAddressFromPrivateKey(PK_FROM);
        assertThat(service.isValidAddress(addr)).isTrue();
    }

    @Test
    void isValidAddress_wrongLengthOrPrefix_invalid() {
        assertThat(service.isValidAddress("T123")).isFalse();
        assertThat(service.isValidAddress("A" + "1".repeat(33))).isFalse();
        assertThat(service.isValidAddress(null)).isFalse();
    }

    @Test
    void isValidAddress_badChecksum_invalid() {
        String addr = TronCryptoUtils.getAddressFromPrivateKey(PK_FROM);
        char last = addr.charAt(addr.length() - 1);
        char swapped = last == 'a' ? 'b' : 'a';
        String tampered = addr.substring(0, addr.length() - 1) + swapped;
        assertThat(service.isValidAddress(tampered)).isFalse();
    }

    @Test
    void getAccountResources_fullResponse_computesAvailableEnergyAndBandwidth() {
        when(tronGrid.walletGetAccountResource("Tuser")).thenReturn(Map.of(
                "EnergyLimit", 100_000, "EnergyUsed", 30_000,
                "freeNetLimit", 600, "freeNetUsed", 100,
                "NetLimit", 1_000, "NetUsed", 200));

        AccountResources r = service.getAccountResources("Tuser");

        assertThat(r.energy()).isEqualTo(70_000L);
        assertThat(r.bandwidth()).isEqualTo(1_300L);
    }

    @Test
    void getAccountResources_missingFields_treatedAsZero() {
        when(tronGrid.walletGetAccountResource("Tuser")).thenReturn(Map.of(
                "freeNetLimit", 600, "freeNetUsed", 50));

        AccountResources r = service.getAccountResources("Tuser");

        assertThat(r.energy()).isEqualTo(0L);
        assertThat(r.bandwidth()).isEqualTo(550L);
    }

    @Test
    void getAccountResources_negativeNetIsClampedToZero() {
        when(tronGrid.walletGetAccountResource("Tuser")).thenReturn(Map.of(
                "EnergyLimit", 10_000, "EnergyUsed", 25_000));

        AccountResources r = service.getAccountResources("Tuser");

        assertThat(r.energy()).isEqualTo(0L);
        assertThat(r.bandwidth()).isEqualTo(0L);
    }

    @Test
    void getAccountResources_nullResponse_returnsEmpty() {
        when(tronGrid.walletGetAccountResource("Tuser")).thenReturn(null);
        assertThat(service.getAccountResources("Tuser")).isEqualTo(AccountResources.EMPTY);
    }

    @Test
    void getAccountResources_rpcThrows_returnsEmpty() {
        when(tronGrid.walletGetAccountResource("Tuser")).thenThrow(new RuntimeException("rpc down"));
        assertThat(service.getAccountResources("Tuser")).isEqualTo(AccountResources.EMPTY);
    }

    @Test
    void generateWallet_producesValidAddressThatRoundTrips() {
        WalletKeys keys = service.generateWallet();
        assertThat(service.isValidAddress(keys.address())).isTrue();
        assertThat(service.deriveAddress(keys.privateKey())).isEqualTo(keys.address());
    }

    @Test
    void getBalance_parsesSunToTrx() {
        when(tronGrid.getV1Account(FROM)).thenReturn(account(12_500_000L));
        assertThat(service.getBalance(FROM)).isEqualTo("12.500000");
    }

    @Test
    void getBalance_missingBalanceField_returnsZero() {
        when(tronGrid.getV1Account(FROM)).thenReturn(Map.of("data", List.of(Map.of())));
        assertThat(service.getBalance(FROM)).isEqualTo("0.000000");
    }

    @Test
    void getBalance_rpcThrows_throwsRpc() {
        when(tronGrid.getV1Account(FROM)).thenThrow(new RuntimeException("rpc down"));
        assertThatThrownBy(() -> service.getBalance(FROM))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void estimateSendFee_isFlatWorstCaseFee() {
        assertThat(service.estimateSendFee()).isEqualByComparingTo("1.5");
    }

    @Test
    void send_invalidPrivateKey_throwsBadRequest() {
        assertThatThrownBy(() -> service.send("not-hex", TO, new BigDecimal("1")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void send_insufficientLocalBalance_throws() {
        when(tronGrid.getV1Account(FROM)).thenReturn(account(5_000_000L));
        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void send_happyPath_signsAndBroadcasts() {
        when(tronGrid.getV1Account(FROM)).thenReturn(account(100_000_000L));
        when(tronGrid.walletCreateTransaction(any()))
                .thenReturn(new HashMap<>(Map.of("txID", TXID)));
        when(tronGrid.walletBroadcastTransaction(any())).thenReturn(Map.of("result", true));

        assertThat(service.send(PK_FROM, TO, new BigDecimal("10"))).isEqualTo(TXID);
    }

    @Test
    void send_createTransactionInsufficient_throwsInsufficient() {
        when(tronGrid.getV1Account(FROM)).thenReturn(account(100_000_000L));
        when(tronGrid.walletCreateTransaction(any()))
                .thenReturn(new HashMap<>(Map.of("Error", "balance is not sufficient")));

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void send_createTransactionOtherError_throwsRpc() {
        when(tronGrid.getV1Account(FROM)).thenReturn(account(100_000_000L));
        when(tronGrid.walletCreateTransaction(any()))
                .thenReturn(new HashMap<>(Map.of("Error", "invalid to_address")));

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void send_nullCreateResponse_throwsRpc() {
        when(tronGrid.getV1Account(FROM)).thenReturn(account(100_000_000L));
        when(tronGrid.walletCreateTransaction(any())).thenReturn(null);

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void send_broadcastRejected_throwsRpc() {
        when(tronGrid.getV1Account(FROM)).thenReturn(account(100_000_000L));
        when(tronGrid.walletCreateTransaction(any()))
                .thenReturn(new HashMap<>(Map.of("txID", TXID)));
        when(tronGrid.walletBroadcastTransaction(any()))
                .thenReturn(Map.of("result", false, "message", "SIGERROR"));

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void send_broadcastInsufficient_throwsInsufficient() {
        when(tronGrid.getV1Account(FROM)).thenReturn(account(100_000_000L));
        when(tronGrid.walletCreateTransaction(any()))
                .thenReturn(new HashMap<>(Map.of("txID", TXID)));
        when(tronGrid.walletBroadcastTransaction(any()))
                .thenReturn(Map.of("result", false, "message", "balance is not sufficient"));

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void getTxStatus_nullOrEmptyInfo_pending() {
        when(tronGrid.walletGetTransactionInfoById(TXID)).thenReturn(null);
        assertThat(service.getTxStatus(TXID, FROM).status()).isEqualTo(OnChainStatus.PENDING);

        when(tronGrid.walletGetTransactionInfoById(TXID)).thenReturn(Map.of());
        assertThat(service.getTxStatus(TXID, FROM).status()).isEqualTo(OnChainStatus.PENDING);
    }

    @Test
    void getTxStatus_noBlockNumber_pending() {
        when(tronGrid.walletGetTransactionInfoById(TXID)).thenReturn(Map.of("id", TXID));
        assertThat(service.getTxStatus(TXID, FROM).status()).isEqualTo(OnChainStatus.PENDING);
    }

    @Test
    void getTxStatus_confirmedTransfer_mapsFeeAndTimestamp() {
        when(tronGrid.walletGetTransactionInfoById(TXID)).thenReturn(Map.of(
                "blockNumber", 123L, "fee", 1_100_000L, "blockTimeStamp", 1_700_000_000_000L));

        TxStatusResult result = service.getTxStatus(TXID, FROM);

        assertThat(result.status()).isEqualTo(OnChainStatus.CONFIRMED);
        assertThat(result.feeNative()).isEqualByComparingTo("1.1");
        assertThat(result.blockTimestamp()).isNotNull();
    }

    @Test
    void getTxStatus_receiptResultNotSuccess_reverted() {
        when(tronGrid.walletGetTransactionInfoById(TXID)).thenReturn(Map.of(
                "blockNumber", 123L, "receipt", Map.of("result", "OUT_OF_ENERGY")));

        assertThat(service.getTxStatus(TXID, FROM).status()).isEqualTo(OnChainStatus.REVERTED);
    }

    @Test
    void getTxStatus_topLevelResultFailed_reverted() {
        when(tronGrid.walletGetTransactionInfoById(TXID)).thenReturn(Map.of(
                "blockNumber", 123L, "result", "FAILED"));

        assertThat(service.getTxStatus(TXID, FROM).status()).isEqualTo(OnChainStatus.REVERTED);
    }

    @Test
    void getTxStatus_plainTransferNoReceiptResult_confirmed() {
        when(tronGrid.walletGetTransactionInfoById(TXID)).thenReturn(Map.of("blockNumber", 123L));

        assertThat(service.getTxStatus(TXID, FROM).status()).isEqualTo(OnChainStatus.CONFIRMED);
    }
}
