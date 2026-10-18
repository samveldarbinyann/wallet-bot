package com.example.walletbot.platform.blockchain.client;

import com.example.walletbot.platform.blockchain.ratelimit.TonCenterRateLimiter;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Timeout(15)
class TonCenterClientTest {

    private MockRestServiceServer mockServer;
    private TonCenterClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new TonCenterClient(restTemplate, "http://ton.test/", "secret-key",
                new TonCenterRateLimiter(0));
    }

    @Test
    void constructor_blankUrl_rejected() {
        assertThatThrownBy(() -> new TonCenterClient(new RestTemplate(), "  ", null, new TonCenterRateLimiter(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TonCenterClient(null, "http://x", null, new TonCenterRateLimiter(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getBalanceTon_ok_convertsNanotonsToTon() {
        mockServer.expect(requestTo("http://ton.test/getAddressBalance?address=EQuser"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "secret-key"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":\"1500000000\"}", MediaType.APPLICATION_JSON));

        assertThat(client.getBalanceTon("EQuser")).isEqualByComparingTo("1.5");
        mockServer.verify();
    }

    @Test
    void getBalanceTon_notOk_throwsRpc() {
        mockServer.expect(requestTo("http://ton.test/getAddressBalance?address=EQuser"))
                .andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getBalanceTon("EQuser"))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void getBalanceTon_serverError_throwsRpc() {
        mockServer.expect(requestTo("http://ton.test/getAddressBalance?address=EQuser"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getBalanceTon("EQuser"))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void getWalletInformation_ok_parsesStateAndSeqno() {
        mockServer.expect(requestTo("http://ton.test/getWalletInformation?address=EQuser"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"account_state\":\"active\",\"seqno\":42}}",
                        MediaType.APPLICATION_JSON));

        TonCenterClient.WalletInformation info = client.getWalletInformation("EQuser");

        assertThat(info.accountState()).isEqualTo("active");
        assertThat(info.seqno()).isEqualTo(42L);
        assertThat(info.isUninitialized()).isFalse();
        mockServer.verify();
    }

    @Test
    void getWalletInformation_uninitialized_hasNullSeqnoAndFlagsUninit() {
        mockServer.expect(requestTo("http://ton.test/getWalletInformation?address=EQuser"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"account_state\":\"uninitialized\"}}",
                        MediaType.APPLICATION_JSON));

        TonCenterClient.WalletInformation info = client.getWalletInformation("EQuser");

        assertThat(info.seqno()).isNull();
        assertThat(info.isUninitialized()).isTrue();
    }

    @Test
    void getWalletInformation_notOk_throwsRpc() {
        mockServer.expect(requestTo("http://ton.test/getWalletInformation?address=EQuser"))
                .andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getWalletInformation("EQuser"))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void getWalletInformation_unexpectedResultShape_throwsRpc() {
        mockServer.expect(requestTo("http://ton.test/getWalletInformation?address=EQuser"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":\"not-a-map\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getWalletInformation("EQuser"))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void sendBocReturnHash_ok_returnsHashAndPostsBoc() {
        mockServer.expect(requestTo("http://ton.test/sendBocReturnHash"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "secret-key"))
                .andExpect(jsonPath("$.boc").value("BOC_BASE64"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"hash\":\"TXHASH==\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.sendBocReturnHash("BOC_BASE64")).isEqualTo("TXHASH==");
        mockServer.verify();
    }

    @Test
    void sendBocReturnHash_noHashInResponse_throwsRpc() {
        mockServer.expect(requestTo("http://ton.test/sendBocReturnHash"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendBocReturnHash("BOC_BASE64"))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void sendBocReturnHash_serverError_throwsRpc() {
        mockServer.expect(requestTo("http://ton.test/sendBocReturnHash"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.sendBocReturnHash("BOC_BASE64"))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void runGetMethodForBalance_ok_parsesHexBalanceFromStack() {
        mockServer.expect(requestTo("http://ton.test/runGetMethod"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.address").value("EQjetton"))
                .andExpect(jsonPath("$.method").value("get_wallet_data"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"exit_code\":0,\"stack\":[[\"num\",\"0x5f5e100\"]]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.runGetMethodForBalance("EQjetton")).isEqualByComparingTo(BigInteger.valueOf(100_000_000));
        mockServer.verify();
    }

    @Test
    void runGetMethodForBalance_nonZeroExitCode_returnsNull() {
        mockServer.expect(requestTo("http://ton.test/runGetMethod"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"exit_code\":-13,\"stack\":[]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.runGetMethodForBalance("EQjetton")).isNull();
    }

    @Test
    void runGetMethodForBalance_notOk_returnsNull() {
        mockServer.expect(requestTo("http://ton.test/runGetMethod"))
                .andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));

        assertThat(client.runGetMethodForBalance("EQjetton")).isNull();
    }

    @Test
    void runGetMethodForJettonWalletAddress_ok_extractsCellBytesFromStack() {
        mockServer.expect(requestTo("http://ton.test/runGetMethod"))
                .andExpect(jsonPath("$.address").value("EQmaster"))
                .andExpect(jsonPath("$.method").value("get_wallet_address"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"exit_code\":0,\"stack\":[[\"cell\",{\"bytes\":\"CELL_BOC\"}]]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.runGetMethodForJettonWalletAddress("EQmaster", "OWNER_BOC")).isEqualTo("CELL_BOC");
        mockServer.verify();
    }

    @Test
    void runGetMethodForJettonWalletAddress_nonZeroExitCode_returnsNull() {
        mockServer.expect(requestTo("http://ton.test/runGetMethod"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"exit_code\":11,\"stack\":[]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.runGetMethodForJettonWalletAddress("EQmaster", "OWNER_BOC")).isNull();
    }

    @Test
    void findTransactionByInMessageHash_matchingHash_returnsTxInfo() {
        mockServer.expect(requestTo("http://ton.test/getTransactions?address=EQuser&limit=100"))
                .andRespond(withSuccess(
                        """
                                {"ok":true,"result":[
                                  {"utime":1700000000,"fee":"5000000","in_msg":{"hash":"OTHER"}},
                                  {"utime":1700000123,"fee":"7000000","in_msg":{"hash":"WANTED"}}
                                ]}
                                """, MediaType.APPLICATION_JSON));

        TonCenterClient.TonTxInfo info = client.findTransactionByInMessageHash("EQuser", "WANTED");

        assertThat(info).isNotNull();
        assertThat(info.utime()).isEqualTo(1700000123L);
        assertThat(info.feeTon()).isEqualByComparingTo("0.007");
        mockServer.verify();
    }

    @Test
    void findTransactionByInMessageHash_noMatch_returnsNull() {
        mockServer.expect(requestTo("http://ton.test/getTransactions?address=EQuser&limit=100"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":[{\"utime\":1,\"in_msg\":{\"hash\":\"OTHER\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.findTransactionByInMessageHash("EQuser", "WANTED")).isNull();
    }

    @Test
    void findTransactionByInMessageHash_notOk_returnsNull() {
        mockServer.expect(requestTo("http://ton.test/getTransactions?address=EQuser&limit=100"))
                .andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));

        assertThat(client.findTransactionByInMessageHash("EQuser", "WANTED")).isNull();
    }
}
