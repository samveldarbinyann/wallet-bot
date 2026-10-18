package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.platform.blockchain.BlockchainService.OnChainStatus;
import com.example.walletbot.platform.blockchain.BlockchainService.TxStatusResult;
import com.example.walletbot.platform.blockchain.BlockchainService.WalletKeys;
import com.example.walletbot.platform.blockchain.cache.SpentUtxoCache;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import org.bitcoinj.params.MainNetParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@Timeout(20)
class BtcBlockchainServiceTest {

    private static final String PK = "59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";
    private static final String TO = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4";
    private static final String TXID = "ab".repeat(32);
    private static final String NODE = "http://getblock/";
    private static final String MEMPOOL = "http://mempool";

    private SpentUtxoCache spentUtxoCache;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private BtcBlockchainService service;
    private String from;

    @BeforeEach
    void setUp() {
        spentUtxoCache = mock(SpentUtxoCache.class);
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        service = new BtcBlockchainService(restTemplate, "http://getblock", spentUtxoCache,
                MainNetParams.get(), MEMPOOL);
        from = service.deriveAddress(PK);
    }

    @Test
    void isValidAddress_mainnetP2pkh_valid() {
        assertThat(service.isValidAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")).isTrue();
    }

    @Test
    void isValidAddress_bech32SegWit_valid() {
        assertThat(service.isValidAddress("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")).isTrue();
    }

    @Test
    void isValidAddress_testnetAddress_rejectedOnMainnet() {
        assertThat(service.isValidAddress("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")).isFalse();
    }

    @Test
    void isValidAddress_garbageOrWrongLength_invalid() {
        assertThat(service.isValidAddress("not-an-address")).isFalse();
        assertThat(service.isValidAddress("123")).isFalse();
        assertThat(service.isValidAddress(null)).isFalse();
        assertThat(service.isValidAddress("  ")).isFalse();
    }

    @Test
    void generateWallet_producesP2wpkhAndRoundTrips() {
        WalletKeys keys = service.generateWallet();
        assertThat(keys.address()).startsWith("bc1q");
        assertThat(service.isValidAddress(keys.address())).isTrue();
        assertThat(service.deriveAddress(keys.privateKey())).isEqualTo(keys.address());
    }

    @Test
    void getBalance_formatsToEightDecimals() {
        server.expect(requestTo(MEMPOOL + "/address/" + from))
                .andRespond(withSuccess(
                        "{\"chain_stats\":{\"funded_txo_sum\":250000000,\"spent_txo_sum\":50000000}}",
                        MediaType.APPLICATION_JSON));

        assertThat(service.getBalance(from)).isEqualTo("2.00000000");
    }

    @Test
    void getBalance_rpcError_returnsZeroString() {
        server.expect(requestTo(MEMPOOL + "/address/" + from)).andRespond(withServerError());
        assertThat(service.getBalance(from)).isEqualTo("0.00000000");
    }

    @Test
    void estimateSendFee_usesDynamicFeeRate() {
        server.expect(requestTo(MEMPOOL + "/v1/fees/recommended"))
                .andRespond(withSuccess("{\"halfHourFee\":10}", MediaType.APPLICATION_JSON));

        assertThat(service.estimateSendFee()).isEqualByComparingTo("0.00001410");
    }

    @Test
    void estimateSendFee_feeApiDown_usesFallbackRate() {
        server.expect(requestTo(MEMPOOL + "/v1/fees/recommended")).andRespond(withServerError());

        assertThat(service.estimateSendFee()).isEqualByComparingTo("0.00002820");
    }

    @Test
    void getTxStatus_confirmed_mapsTimestampAndFee() {
        server.expect(requestTo(MEMPOOL + "/tx/" + TXID + "/status"))
                .andRespond(withSuccess(
                        "{\"confirmed\":true,\"block_time\":1700000000}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(MEMPOOL + "/tx/" + TXID))
                .andRespond(withSuccess("{\"fee\":3000}", MediaType.APPLICATION_JSON));

        TxStatusResult result = service.getTxStatus(TXID, from);

        assertThat(result.status()).isEqualTo(OnChainStatus.CONFIRMED);
        assertThat(result.blockTimestamp()).isNotNull();
        assertThat(result.feeNative()).isEqualByComparingTo("0.00003000");
    }

    @Test
    void getTxStatus_unknownTx_pending() {
        server.expect(requestTo(MEMPOOL + "/tx/" + TXID + "/status"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(service.getTxStatus(TXID, from).status()).isEqualTo(OnChainStatus.PENDING);
    }

    @Test
    void getTxStatus_notYetConfirmed_pending() {
        server.expect(requestTo(MEMPOOL + "/tx/" + TXID + "/status"))
                .andRespond(withSuccess("{\"confirmed\":false}", MediaType.APPLICATION_JSON));

        assertThat(service.getTxStatus(TXID, from).status()).isEqualTo(OnChainStatus.PENDING);
    }

    @Test
    void send_happyPath_selectsUtxoSignsAndBroadcasts() {
        when(spentUtxoCache.isAvailable(anyString(), anyLong())).thenReturn(true);
        expectUtxoFetch(1_000_000L, true);
        expectGettxoutUnspent();
        expectFeeRate(10);
        server.expect(requestTo(MEMPOOL + "/tx"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("broadcastTxId", MediaType.TEXT_PLAIN));

        String txId = service.send(PK, TO, new BigDecimal("0.005"));

        assertThat(txId).isEqualTo("broadcastTxId");
        verify(spentUtxoCache).markAsSpent(eq(TXID), eq(0L));
    }

    @Test
    void send_noAvailableUtxos_throwsInsufficient() {
        when(spentUtxoCache.isAvailable(anyString(), anyLong())).thenReturn(true);
        server.expect(requestTo(MEMPOOL + "/address/" + from + "/utxo"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.send(PK, TO, new BigDecimal("0.005")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void send_utxoTooSmallForAmountPlusFee_throwsInsufficient() {
        when(spentUtxoCache.isAvailable(anyString(), anyLong())).thenReturn(true);
        expectUtxoFetch(1_000L, true);
        expectGettxoutUnspent();
        expectFeeRate(10);

        assertThatThrownBy(() -> service.send(PK, TO, new BigDecimal("0.005")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void sendWithMemo_memoTooLong_throwsBeforeAnyRpc() {
        String memo = "x".repeat(81);
        assertThatThrownBy(() -> service.sendWithMemo(PK, TO, new BigDecimal("0.005"), memo))
                .isInstanceOf(BlockchainRpcException.class);
    }

    private void expectUtxoFetch(long valueSat, boolean confirmed) {
        server.expect(requestTo(MEMPOOL + "/address/" + from + "/utxo"))
                .andRespond(withSuccess(
                        "[{\"txid\":\"" + TXID + "\",\"vout\":0,\"value\":" + valueSat
                                + ",\"status\":{\"confirmed\":" + confirmed + "}}]",
                        MediaType.APPLICATION_JSON));
    }

    private void expectGettxoutUnspent() {
        server.expect(requestTo(NODE))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"result\":{\"value\":0.01},\"error\":null}", MediaType.APPLICATION_JSON));
    }

    private void expectFeeRate(int halfHourFee) {
        server.expect(requestTo(MEMPOOL + "/v1/fees/recommended"))
                .andRespond(withSuccess("{\"halfHourFee\":" + halfHourFee + "}", MediaType.APPLICATION_JSON));
    }
}
