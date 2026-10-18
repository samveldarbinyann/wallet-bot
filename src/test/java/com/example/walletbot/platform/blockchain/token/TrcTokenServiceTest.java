package com.example.walletbot.platform.blockchain.token;

import com.example.walletbot.platform.blockchain.chain.TrxBlockchainService;
import com.example.walletbot.platform.blockchain.chain.TrxBlockchainService.AccountResources;
import com.example.walletbot.platform.blockchain.client.TronGridClient;
import com.example.walletbot.platform.blockchain.util.TronCryptoUtils;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrcTokenServiceTest {

    private static final String USDT_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
    private static final String PK_FROM = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String PK_TO = "0000000000000000000000000000000000000000000000000000000000000002";
    private static final String FROM = TronCryptoUtils.getAddressFromPrivateKey(PK_FROM);
    private static final String TO = TronCryptoUtils.getAddressFromPrivateKey(PK_TO);
    private static final String TXID = "a1b2c3d4".repeat(8);

    @Mock
    TrxBlockchainService trxService;
    @Mock
    TronGridClient tronGrid;

    private TrcTokenService service;

    private static Map<String, Object> account(long usdtRaw, long trxSun) {
        return Map.of("data", List.of(Map.of(
                "balance", trxSun,
                "trc20", List.of(Map.of(USDT_CONTRACT, Long.toString(usdtRaw)))
        )));
    }

    @BeforeEach
    void setUp() {
        service = new TrcTokenService(BlockchainNetwork.USDT_TRC20, trxService,
                tronGrid, USDT_CONTRACT);
    }

    @Test
    void estimateNativeBurn_delegatedEnergyAndBandwidth_burnsZero() {
        when(trxService.getAccountResources("Tuser")).thenReturn(new AccountResources(200_000, 1_000));

        assertThat(service.estimateNativeBurn("Tuser", null, new BigDecimal("10")))
                .isEqualByComparingTo("0");
    }

    @Test
    void estimateNativeBurn_noResources_pricesWorstCaseBurn() {
        when(trxService.getAccountResources("Tuser")).thenReturn(AccountResources.EMPTY);

        assertThat(service.estimateNativeBurn("Tuser", null, new BigDecimal("10")))
                .isEqualByComparingTo("13.345");
    }

    @Test
    void estimateNativeBurn_energyCoveredButNoBandwidth_chargesBandwidthOnly() {
        when(trxService.getAccountResources("Tuser")).thenReturn(new AccountResources(130_000, 0));

        assertThat(service.estimateNativeBurn("Tuser", null, new BigDecimal("10")))
                .isEqualByComparingTo("0.345");
    }

    @Test
    void estimateNativeBurn_partialEnergyAndPartialBandwidth_chargesBothShortfalls() {
        when(trxService.getAccountResources("Tuser")).thenReturn(new AccountResources(100_000, 300));

        assertThat(service.estimateNativeBurn("Tuser", null, new BigDecimal("10")))
                .isEqualByComparingTo("3.045");
    }

    @Test
    void estimateNativeBurn_blankSender_assumesNoResources() {
        assertThat(service.estimateNativeBurn("", null, new BigDecimal("10")))
                .isEqualByComparingTo("13.345");
    }

    @Test
    void estimateNativeBurn_knownRecipient_usesConstantContractEstimate() {
        when(trxService.getAccountResources(FROM)).thenReturn(AccountResources.EMPTY);
        when(tronGrid.walletTriggerConstantContract(any()))
                .thenReturn(Map.of("energy_used", 64_285));

        assertThat(service.estimateNativeBurn(FROM, TO, new BigDecimal("10")))
                .isEqualByComparingTo("7.4164");
    }

    @Test
    void estimateTransferEnergy_unknownRecipient_returnsFreshRecipientFallback() {
        assertThat(service.estimateTransferEnergy("Tuser", null, new BigDecimal("10"))).isEqualTo(130_000L);
        assertThat(service.estimateTransferEnergy(null, "Tdest", new BigDecimal("10"))).isEqualTo(130_000L);
    }

    @Test
    void estimateTransferEnergy_constantContractSuccess_appliesMargin() {
        when(tronGrid.walletTriggerConstantContract(any()))
                .thenReturn(Map.of("energy_used", 64_285));

        assertThat(service.estimateTransferEnergy(FROM, TO, new BigDecimal("10"))).isEqualTo(70_714L);
    }

    @Test
    void estimateTransferEnergy_constantContractZero_fallsBackToStatic() {
        when(tronGrid.walletTriggerConstantContract(any()))
                .thenReturn(Map.of("energy_used", 0));

        assertThat(service.estimateTransferEnergy(FROM, TO, new BigDecimal("10"))).isEqualTo(130_000L);
    }

    @Test
    void estimateTransferEnergy_constantContractFails_fallsBackToStatic() {
        when(tronGrid.walletTriggerConstantContract(any()))
                .thenThrow(new RuntimeException("rpc down"));

        assertThat(service.estimateTransferEnergy(FROM, TO, new BigDecimal("10"))).isEqualTo(130_000L);
    }

    @Test
    void send_delegatedEnergy_zeroTrx_broadcastsSuccessfully() {
        when(trxService.getAccountData(FROM)).thenReturn(account(100_000_000L, 0L));
        when(trxService.getAccountResources(FROM)).thenReturn(new AccountResources(200_000, 1_000));
        when(tronGrid.walletTriggerConstantContract(any())).thenReturn(Map.of("energy_used", 64_285));
        when(tronGrid.walletTriggerSmartContract(any()))
                .thenReturn(Map.of("transaction", new java.util.HashMap<>(Map.of("txID", TXID))));
        when(tronGrid.walletBroadcastTransaction(any())).thenReturn(Map.of("result", true));

        String txId = service.send(PK_FROM, TO, new BigDecimal("10"));

        assertThat(txId).isEqualTo(TXID);
        verify(tronGrid).walletBroadcastTransaction(any());
    }

    @Test
    void send_insufficientUsdt_throwsBeforeAnyBroadcast() {
        when(trxService.getAccountData(FROM)).thenReturn(account(1_000_000L, 0L));

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient USDT");

        verify(tronGrid, never()).walletTriggerSmartContract(any());
        verify(tronGrid, never()).walletBroadcastTransaction(any());
    }

    @Test
    void send_noEnergyNoTrx_throwsInsufficientTrxForFee() {
        when(trxService.getAccountData(FROM)).thenReturn(account(100_000_000L, 0L));
        when(trxService.getAccountResources(FROM)).thenReturn(AccountResources.EMPTY);
        when(tronGrid.walletTriggerConstantContract(any())).thenReturn(Map.of("energy_used", 64_285));

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient TRX for fee");

        verify(tronGrid, never()).walletBroadcastTransaction(any());
    }

    @Test
    void send_ownTrxCoversFee_broadcastsSuccessfully() {
        when(trxService.getAccountData(FROM)).thenReturn(account(100_000_000L, 20_000_000L));
        when(trxService.getAccountResources(FROM)).thenReturn(AccountResources.EMPTY);
        when(tronGrid.walletTriggerConstantContract(any())).thenReturn(Map.of("energy_used", 64_285));
        when(tronGrid.walletTriggerSmartContract(any()))
                .thenReturn(Map.of("transaction", new java.util.HashMap<>(Map.of("txID", TXID))));
        when(tronGrid.walletBroadcastTransaction(any())).thenReturn(Map.of("result", true));

        assertThat(service.send(PK_FROM, TO, new BigDecimal("10"))).isEqualTo(TXID);
    }

    @Test
    void send_contractCreationReturnsError_throwsRpc() {
        when(trxService.getAccountData(FROM)).thenReturn(account(100_000_000L, 0L));
        when(trxService.getAccountResources(FROM)).thenReturn(new AccountResources(200_000, 1_000));
        when(tronGrid.walletTriggerConstantContract(any())).thenReturn(Map.of("energy_used", 64_285));
        when(tronGrid.walletTriggerSmartContract(any())).thenReturn(Map.of("Error", "bad contract"));

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(BlockchainRpcException.class)
                .hasMessageContaining("Transaction creation failed");
    }

    @Test
    void send_broadcastRejected_throwsRpc() {
        when(trxService.getAccountData(FROM)).thenReturn(account(100_000_000L, 0L));
        when(trxService.getAccountResources(FROM)).thenReturn(new AccountResources(200_000, 1_000));
        when(tronGrid.walletTriggerConstantContract(any())).thenReturn(Map.of("energy_used", 64_285));
        when(tronGrid.walletTriggerSmartContract(any()))
                .thenReturn(Map.of("transaction", new java.util.HashMap<>(Map.of("txID", TXID))));
        when(tronGrid.walletBroadcastTransaction(any()))
                .thenReturn(Map.of("result", false, "message", "SIGERROR"));

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(BlockchainRpcException.class)
                .hasMessageContaining("broadcast failed");
    }

    @Test
    void send_nullAccountData_throwsRpc() {
        when(trxService.getAccountData(FROM)).thenReturn(null);

        assertThatThrownBy(() -> service.send(PK_FROM, TO, new BigDecimal("10")))
                .isInstanceOf(BlockchainRpcException.class)
                .hasMessageContaining("account data");
    }
}
