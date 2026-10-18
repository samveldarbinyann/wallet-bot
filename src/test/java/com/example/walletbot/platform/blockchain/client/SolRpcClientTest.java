package com.example.walletbot.platform.blockchain.client;

import com.example.walletbot.platform.blockchain.client.SolRpcClient.SignatureStatus;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Timeout(15)
class SolRpcClientTest {

    private static final String URL = "http://sol.rpc";

    private MockRestServiceServer server;
    private SolRpcClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new SolRpcClient(restTemplate, URL);
    }

    @Test
    void constructor_rejectsNullTemplateAndBlankUrl() {
        assertThatThrownBy(() -> new SolRpcClient(null, URL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SolRpcClient(new RestTemplate(), "  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getLatestBlockhash_ok_returnsBlockhash() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.method").value("getLatestBlockhash"))
                .andRespond(withSuccess(
                        "{\"result\":{\"value\":{\"blockhash\":\"BLKHASH123\"}}}", MediaType.APPLICATION_JSON));

        assertThat(client.getLatestBlockhash()).isEqualTo("BLKHASH123");
        server.verify();
    }

    @Test
    void getLatestBlockhash_missingValue_throwsRpc() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getLatestBlockhash()).isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void getLatestBlockhash_httpError_throwsRpc() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> client.getLatestBlockhash()).isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void getSignatureStatus_finalized_parsesSlotAndNotErrored() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.method").value("getSignatureStatuses"))
                .andRespond(withSuccess(
                        "{\"result\":{\"value\":[{\"confirmationStatus\":\"finalized\",\"slot\":12345,\"err\":null}]}}",
                        MediaType.APPLICATION_JSON));

        SignatureStatus status = client.getSignatureStatus("SIG");

        assertThat(status).isNotNull();
        assertThat(status.isFinalized()).isTrue();
        assertThat(status.isErrored()).isFalse();
        assertThat(status.slot()).isEqualTo(12345L);
    }

    @Test
    void getSignatureStatus_withErr_isErrored() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"result\":{\"value\":[{\"confirmationStatus\":\"finalized\",\"err\":{\"InstructionError\":[0,\"Custom\"]}}]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getSignatureStatus("SIG").isErrored()).isTrue();
    }

    @Test
    void getSignatureStatus_emptyValue_returnsNull() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"result\":{\"value\":[]}}", MediaType.APPLICATION_JSON));

        assertThat(client.getSignatureStatus("SIG")).isNull();
    }

    @Test
    void getSignatureStatus_httpError_returnsNull() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThat(client.getSignatureStatus("SIG")).isNull();
    }

    @Test
    void getBlockTime_ok_returnsEpochSeconds() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.method").value("getBlockTime"))
                .andRespond(withSuccess("{\"result\":1700000000}", MediaType.APPLICATION_JSON));

        assertThat(client.getBlockTime(999L)).isEqualTo(1_700_000_000L);
    }

    @Test
    void getBlockTime_nullResult_returnsNull() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"result\":null}", MediaType.APPLICATION_JSON));

        assertThat(client.getBlockTime(999L)).isNull();
    }

    @Test
    void getPriorityFeeEstimate_ok_returnsEstimate() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.method").value("getPriorityFeeEstimate"))
                .andRespond(withSuccess(
                        "{\"result\":{\"priorityFeeEstimate\":2500}}", MediaType.APPLICATION_JSON));

        assertThat(client.getPriorityFeeEstimate()).isEqualTo(2500L);
    }

    @Test
    void getPriorityFeeEstimate_missingField_fallsBackToDefault() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        assertThat(client.getPriorityFeeEstimate()).isEqualTo(1000L);
    }

    @Test
    void getPriorityFeeEstimate_httpError_fallsBackToDefault() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThat(client.getPriorityFeeEstimate()).isEqualTo(1000L);
    }

    @Test
    void sendRawTransaction_ok_returnsSignature() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.method").value("sendTransaction"))
                .andRespond(withSuccess("{\"result\":\"SIG123\"}", MediaType.APPLICATION_JSON));

        assertThat(client.sendRawTransaction("BASE64TX")).isEqualTo("SIG123");
        server.verify();
    }

    @Test
    void sendRawTransaction_rpcError_throws() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"error\":{\"code\":-32002,\"message\":\"blockhash not found\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendRawTransaction("BASE64TX"))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void confirmTransaction_confirmedImmediately_returnsWithoutRebroadcast() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.method").value("getSignatureStatuses"))
                .andRespond(withSuccess(
                        "{\"result\":{\"value\":[{\"confirmationStatus\":\"confirmed\",\"err\":null}]}}",
                        MediaType.APPLICATION_JSON));

        client.confirmTransaction("SIG", "BASE64TX");
        server.verify();
    }

    @Test
    void confirmTransaction_reverted_throws() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"result\":{\"value\":[{\"confirmationStatus\":\"confirmed\",\"err\":{\"x\":1}}]}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.confirmTransaction("SIG", "BASE64TX"))
                .isInstanceOf(BlockchainRpcException.class)
                .hasMessageContaining("reverted");
    }

    @Test
    void confirmTransaction_neverConfirms_rebroadcastsThenThrows() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer s = MockRestServiceServer.createServer(restTemplate);
        SolRpcClient fastClient = new SolRpcClient(restTemplate, URL, 1L, 1);

        s.expect(requestTo(URL)).andExpect(jsonPath("$.method").value("getSignatureStatuses"))
                .andRespond(withSuccess("{\"result\":{\"value\":[]}}", MediaType.APPLICATION_JSON));
        s.expect(requestTo(URL)).andExpect(jsonPath("$.method").value("sendTransaction"))
                .andRespond(withSuccess("{\"result\":\"SIG\"}", MediaType.APPLICATION_JSON));
        s.expect(requestTo(URL)).andExpect(jsonPath("$.method").value("getSignatureStatuses"))
                .andRespond(withSuccess("{\"result\":{\"value\":[]}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fastClient.confirmTransaction("SIG", "BASE64TX"))
                .isInstanceOf(BlockchainRpcException.class)
                .hasMessageContaining("not confirmed");
        s.verify();
    }
}
