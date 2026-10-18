package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.platform.blockchain.BlockchainService.OnChainStatus;
import com.example.walletbot.platform.blockchain.BlockchainService.TxStatusResult;
import com.example.walletbot.platform.blockchain.BlockchainService.WalletKeys;
import com.example.walletbot.platform.blockchain.client.SolRpcClient;
import com.example.walletbot.platform.blockchain.client.SolRpcClient.SignatureStatus;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Timeout(20)
class SolBlockchainServiceTest {

    private static final String VALID_PUBKEY = "11111111111111111111111111111111";

    private SolRpcClient rpc;
    private JsonRpcStubServer server;
    private SolBlockchainService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new JsonRpcStubServer();
        rpc = mock(SolRpcClient.class);
        when(rpc.getRpcUrl()).thenReturn(server.url());
        service = new SolBlockchainService(rpc);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void isValidAddress_validBase58PublicKey_valid() {
        assertThat(service.isValidAddress(VALID_PUBKEY)).isTrue();
    }

    @Test
    void isValidAddress_tooShortOrTooLong_invalid() {
        assertThat(service.isValidAddress("1111")).isFalse();
        assertThat(service.isValidAddress("1".repeat(60))).isFalse();
    }

    @Test
    void isValidAddress_nonBase58Chars_invalid() {
        assertThat(service.isValidAddress("0OIl0OIl0OIl0OIl0OIl0OIl0OIl0OIl")).isFalse();
    }

    @Test
    void isValidAddress_null_invalid() {
        assertThat(service.isValidAddress(null)).isFalse();
    }

    @Test
    void generateWallet_roundTripsAddressFromSecretKey() {
        WalletKeys keys = service.generateWallet();
        assertThat(service.isValidAddress(keys.address())).isTrue();
        assertThat(service.deriveAddress(keys.privateKey())).isEqualTo(keys.address());
    }

    @Test
    void estimateSendFee_combinesBaseFeeAndPriorityFee() {
        when(rpc.getPriorityFeeEstimate()).thenReturn(1000L);
        assertThat(service.estimateSendFee()).isEqualByComparingTo("0.0000052");
    }

    @Test
    void getTxStatus_unknownSignature_pending() {
        when(rpc.getSignatureStatus("sig")).thenReturn(null);
        assertThat(service.getTxStatus("sig", null).status()).isEqualTo(OnChainStatus.PENDING);
    }

    @Test
    void getTxStatus_errored_reverted() {
        when(rpc.getSignatureStatus("sig")).thenReturn(new SignatureStatus("finalized", "InstructionError", 1L));
        TxStatusResult result = service.getTxStatus("sig", null);
        assertThat(result.status()).isEqualTo(OnChainStatus.REVERTED);
        assertThat(result.feeNative()).isEqualByComparingTo("0.000005");
    }

    @Test
    void getTxStatus_notYetFinalized_pending() {
        when(rpc.getSignatureStatus("sig")).thenReturn(new SignatureStatus("processed", null, 1L));
        assertThat(service.getTxStatus("sig", null).status()).isEqualTo(OnChainStatus.PENDING);
    }

    @Test
    void getTxStatus_finalizedWithBlockTime_confirmed() {
        when(rpc.getSignatureStatus("sig")).thenReturn(new SignatureStatus("finalized", null, 100L));
        when(rpc.getBlockTime(100L)).thenReturn(1_700_000_000L);

        TxStatusResult result = service.getTxStatus("sig", null);

        assertThat(result.status()).isEqualTo(OnChainStatus.CONFIRMED);
        assertThat(result.blockTimestamp()).isNotNull();
        assertThat(result.feeNative()).isEqualByComparingTo("0.000005");
    }

    @Test
    void getTxStatus_finalizedNullSlot_confirmedWithoutTimestamp() {
        when(rpc.getSignatureStatus("sig")).thenReturn(new SignatureStatus("finalized", null, null));
        TxStatusResult result = service.getTxStatus("sig", null);
        assertThat(result.status()).isEqualTo(OnChainStatus.CONFIRMED);
        assertThat(result.blockTimestamp()).isNull();
    }

    @Test
    void send_invalidPrivateKey_throwsBadRequest() {
        assertThatThrownBy(() -> service.send("@@not-base64@@", VALID_PUBKEY, new BigDecimal("1")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void send_insufficientForAmountPlusFee_throws() {
        String pk = service.generateWallet().privateKey();
        when(rpc.getPriorityFeeEstimate()).thenReturn(1000L);
        server.on("getBalance", "{\"value\":1000}");

        assertThatThrownBy(() -> service.send(pk, VALID_PUBKEY, new BigDecimal("1")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void send_remainderBelowRentExemption_throws() {
        String pk = service.generateWallet().privateKey();
        when(rpc.getPriorityFeeEstimate()).thenReturn(1000L);
        server.on("getBalance", "{\"value\":1000000}");

        assertThatThrownBy(() -> service.send(pk, VALID_PUBKEY, new BigDecimal("0.0002")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("rent exemption");
    }

    @Test
    void send_happyPath_signsAndReturnsSignature() {
        String pk = service.generateWallet().privateKey();
        when(rpc.getPriorityFeeEstimate()).thenReturn(1000L);
        when(rpc.getLatestBlockhash()).thenReturn(VALID_PUBKEY);
        server.on("getBalance", "{\"value\":5000000000}");
        server.on("getLatestBlockhash",
                "{\"value\":{\"blockhash\":\"" + VALID_PUBKEY + "\",\"lastValidBlockHeight\":1}}");
        server.on("sendTransaction", "\"SoLSignature111\"");

        assertThat(service.send(pk, VALID_PUBKEY, new BigDecimal("1"))).isEqualTo("SoLSignature111");
    }

    private static final class JsonRpcStubServer {
        private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");

        private final HttpServer server;
        private final Map<String, String> results = new HashMap<>();

        JsonRpcStubServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String req = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Matcher m = METHOD.matcher(req);
                String method = m.find() ? m.group(1) : "";
                String result = results.getOrDefault(method, "null");
                byte[] out = ("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":" + result + "}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            });
            server.start();
        }

        void on(String method, String rawResultJson) {
            results.put(method, rawResultJson);
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void close() {
            server.stop(0);
        }
    }
}
