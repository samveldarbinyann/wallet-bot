package com.example.walletbot.platform.blockchain.client;

import com.example.walletbot.platform.blockchain.client.GetBlockClient.BtcTxStatus;
import com.example.walletbot.platform.blockchain.client.GetBlockClient.ParsedUtxo;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@Timeout(15)
class GetBlockClientTest {

    private static final String ADDR = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4";
    private static final String TXID = "ab".repeat(32);
    private static final String NODE = "http://getblock/";
    private static final String MEMPOOL = "http://mempool";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private GetBlockClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        client = new GetBlockClient(restTemplate, "http://getblock", MEMPOOL);
    }

    @Test
    void constructor_rejectsNullTemplateAndBlankUrl() {
        assertThatThrownBy(() -> new GetBlockClient(null, "http://x", MEMPOOL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GetBlockClient(new RestTemplate(), "  ", MEMPOOL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getBtcBalance_subtractsSpentFromFunded() {
        server.expect(requestTo(MEMPOOL + "/address/" + ADDR))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"chain_stats\":{\"funded_txo_sum\":100000000,\"spent_txo_sum\":40000000}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getBtcBalance(ADDR)).isEqualByComparingTo("0.6");
        server.verify();
    }

    @Test
    void getBtcBalance_missingChainStats_returnsZero() {
        server.expect(requestTo(MEMPOOL + "/address/" + ADDR))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.getBtcBalance(ADDR)).isEqualByComparingTo("0");
    }

    @Test
    void getBtcBalance_httpError_returnsZero() {
        server.expect(requestTo(MEMPOOL + "/address/" + ADDR)).andRespond(withServerError());
        assertThat(client.getBtcBalance(ADDR)).isEqualByComparingTo("0");
    }

    @Test
    void getBtcUtxos_confirmedAndUnspent_included() {
        server.expect(requestTo(MEMPOOL + "/address/" + ADDR + "/utxo"))
                .andRespond(withSuccess(
                        "[{\"txid\":\"" + TXID + "\",\"vout\":0,\"value\":1000000,"
                                + "\"status\":{\"confirmed\":true}}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(NODE))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"result\":{\"value\":0.01},\"error\":null}", MediaType.APPLICATION_JSON));

        List<ParsedUtxo> utxos = client.getBtcUtxos(ADDR);

        assertThat(utxos).hasSize(1);
        assertThat(utxos.getFirst().index()).isZero();
        assertThat(utxos.getFirst().value().value).isEqualTo(1_000_000L);
        server.verify();
    }

    @Test
    void getBtcUtxos_unconfirmed_excluded() {
        server.expect(requestTo(MEMPOOL + "/address/" + ADDR + "/utxo"))
                .andRespond(withSuccess(
                        "[{\"txid\":\"" + TXID + "\",\"vout\":0,\"value\":1000000,"
                                + "\"status\":{\"confirmed\":false}}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getBtcUtxos(ADDR)).isEmpty();
        server.verify();
    }

    @Test
    void getBtcUtxos_spentPerGettxout_excluded() {
        server.expect(requestTo(MEMPOOL + "/address/" + ADDR + "/utxo"))
                .andRespond(withSuccess(
                        "[{\"txid\":\"" + TXID + "\",\"vout\":0,\"value\":1000000,"
                                + "\"status\":{\"confirmed\":true}}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(NODE))
                .andRespond(withSuccess("{\"result\":null}", MediaType.APPLICATION_JSON));

        assertThat(client.getBtcUtxos(ADDR)).isEmpty();
        server.verify();
    }

    @Test
    void getBtcUtxos_httpError_throwsRpc() {
        server.expect(requestTo(MEMPOOL + "/address/" + ADDR + "/utxo")).andRespond(withServerError());

        assertThatThrownBy(() -> client.getBtcUtxos(ADDR)).isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void getBtcTxStatus_confirmed_enrichesWithFee() {
        server.expect(requestTo(MEMPOOL + "/tx/" + TXID + "/status"))
                .andRespond(withSuccess(
                        "{\"confirmed\":true,\"block_time\":1700000000}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(MEMPOOL + "/tx/" + TXID))
                .andRespond(withSuccess("{\"fee\":2500}", MediaType.APPLICATION_JSON));

        BtcTxStatus status = client.getBtcTxStatus(TXID);

        assertThat(status).isNotNull();
        assertThat(status.confirmed()).isTrue();
        assertThat(status.blockTime()).isEqualTo(1_700_000_000L);
        assertThat(status.feeSatoshi()).isEqualTo(2_500L);
        server.verify();
    }

    @Test
    void getBtcTxStatus_notConfirmed_noFeeLookup() {
        server.expect(requestTo(MEMPOOL + "/tx/" + TXID + "/status"))
                .andRespond(withSuccess("{\"confirmed\":false}", MediaType.APPLICATION_JSON));

        BtcTxStatus status = client.getBtcTxStatus(TXID);

        assertThat(status.confirmed()).isFalse();
        assertThat(status.feeSatoshi()).isNull();
        server.verify();
    }

    @Test
    void getBtcTxStatus_notFound_returnsNull() {
        server.expect(requestTo(MEMPOOL + "/tx/" + TXID + "/status"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getBtcTxStatus(TXID)).isNull();
    }

    @Test
    void getRecommendedFeePerByte_returnsBody() {
        server.expect(requestTo(MEMPOOL + "/v1/fees/recommended"))
                .andRespond(withSuccess("{\"halfHourFee\":15,\"fastestFee\":25}", MediaType.APPLICATION_JSON));

        Map<String, Object> fees = client.getRecommendedFeePerByte();
        assertThat(fees).containsEntry("halfHourFee", 15);
    }

    @Test
    void broadcastViaMempoolSpace_success_returnsTrimmedTxId() {
        server.expect(requestTo(MEMPOOL + "/tx"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("  deadbeefTxId  ", MediaType.TEXT_PLAIN));

        assertThat(client.broadcastBtcViaMempoolSpace("0xabcdef")).isEqualTo("deadbeefTxId");
        server.verify();
    }

    @Test
    void broadcastViaMempoolSpace_httpError_throwsWithBody() {
        server.expect(requestTo(MEMPOOL + "/tx"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("min relay fee not met"));

        assertThatThrownBy(() -> client.broadcastBtcViaMempoolSpace("abcdef"))
                .isInstanceOf(BlockchainRpcException.class)
                .hasMessageContaining("min relay fee not met");
    }

    @Test
    void broadcastViaGetBlock_success_returnsTxId() {
        server.expect(requestTo(NODE))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"result\":\"gbTxId\",\"error\":null}", MediaType.APPLICATION_JSON));

        assertThat(client.broadcastBtcViaGetBlock("abcdef")).isEqualTo("gbTxId");
    }

    @Test
    void broadcastViaGetBlock_errorField_throws() {
        server.expect(requestTo(NODE))
                .andRespond(withSuccess(
                        "{\"result\":null,\"error\":{\"code\":-25,\"message\":\"bad-txns\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.broadcastBtcViaGetBlock("abcdef"))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void hashFromDisplayTxid_roundTripsDisplayHex() {
        assertThat(GetBlockClient.hashFromDisplayTxid(TXID).toString()).isEqualTo(TXID);
        assertThat(GetBlockClient.hashFromDisplayTxid("0x" + TXID).toString()).isEqualTo(TXID);
    }
}
