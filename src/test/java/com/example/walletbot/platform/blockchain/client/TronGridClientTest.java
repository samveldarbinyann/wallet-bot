package com.example.walletbot.platform.blockchain.client;

import com.example.walletbot.platform.blockchain.ratelimit.TronGridRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TronGridClientTest {

    private MockRestServiceServer mockServer;
    private TronGridClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new TronGridClient(restTemplate, "http://tron.test", "api-key", mock(TronGridRateLimiter.class));
    }

    @Test
    void walletGetAccountResource_postsAddressAndApiKey_returnsBody() {
        mockServer.expect(requestTo("http://tron.test/wallet/getaccountresource"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("TRON-PRO-API-KEY", "api-key"))
                .andExpect(jsonPath("$.address").value("Tuser"))
                .andExpect(jsonPath("$.visible").value(true))
                .andRespond(withSuccess(
                        "{\"EnergyLimit\":100000,\"EnergyUsed\":30000,\"freeNetLimit\":600}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> body = client.walletGetAccountResource("Tuser");

        assertThat(body).containsEntry("EnergyLimit", 100000).containsEntry("EnergyUsed", 30000);
        mockServer.verify();
    }

    @Test
    void walletTriggerConstantContract_postsBody_returnsEnergyUsed() {
        mockServer.expect(requestTo("http://tron.test/wallet/triggerconstantcontract"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.owner_address").value("Tuser"))
                .andRespond(withSuccess(
                        "{\"energy_used\":64285,\"result\":{\"result\":true}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> body = client.walletTriggerConstantContract(Map.of(
                "owner_address", "Tuser",
                "contract_address", "Tcontract",
                "function_selector", "transfer(address,uint256)",
                "parameter", "00",
                "visible", true));

        assertThat(body).containsEntry("energy_used", 64285);
        mockServer.verify();
    }

    @Test
    void getV1Account_getsWithApiKeyHeader_returnsBody() {
        mockServer.expect(requestTo("http://tron.test/v1/accounts/Tuser"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("TRON-PRO-API-KEY", "api-key"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"balance\":12500000}]}", MediaType.APPLICATION_JSON));

        Map<String, Object> body = client.getV1Account("Tuser");

        assertThat(body).containsKey("data");
        mockServer.verify();
    }

    @Test
    void walletCreateTransaction_postsOwnerAndAmount_returnsTxId() {
        mockServer.expect(requestTo("http://tron.test/wallet/createtransaction"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.owner_address").value("Tfrom"))
                .andExpect(jsonPath("$.to_address").value("Tto"))
                .andExpect(jsonPath("$.amount").value(1000000))
                .andRespond(withSuccess("{\"txID\":\"abcd\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> body = client.walletCreateTransaction(Map.of(
                "owner_address", "Tfrom", "to_address", "Tto", "amount", 1_000_000, "visible", true));

        assertThat(body).containsEntry("txID", "abcd");
        mockServer.verify();
    }

    @Test
    void walletBroadcastTransaction_postsSignedTx_returnsResult() {
        mockServer.expect(requestTo("http://tron.test/wallet/broadcasttransaction"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.txID").value("abcd"))
                .andRespond(withSuccess("{\"result\":true}", MediaType.APPLICATION_JSON));

        Map<String, Object> body = client.walletBroadcastTransaction(Map.of("txID", "abcd"));

        assertThat(body).containsEntry("result", true);
        mockServer.verify();
    }

    @Test
    void walletGetTransactionInfoById_postsValue_returnsInfo() {
        mockServer.expect(requestTo("http://tron.test/wallet/gettransactioninfobyid"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.value").value("abcd"))
                .andRespond(withSuccess(
                        "{\"blockNumber\":123,\"fee\":1100000}", MediaType.APPLICATION_JSON));

        Map<String, Object> body = client.walletGetTransactionInfoById("abcd");

        assertThat(body).containsEntry("blockNumber", 123);
        mockServer.verify();
    }

    @Test
    void noApiKey_omitsApiKeyHeader() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer srv = MockRestServiceServer.createServer(rt);
        TronGridClient keyless = new TronGridClient(rt, "http://tron.test", null, mock(TronGridRateLimiter.class));
        srv.expect(requestTo("http://tron.test/v1/accounts/Tuser"))
                .andExpect(headerDoesNotExist("TRON-PRO-API-KEY"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        keyless.getV1Account("Tuser");
        srv.verify();
    }

    @Test
    void constructor_rejectsNullTemplateAndBlankUrl() {
        assertThatThrownBy(() -> new TronGridClient(null, "http://x", "k", mock(TronGridRateLimiter.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TronGridClient(new RestTemplate(), "  ", "k", mock(TronGridRateLimiter.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
