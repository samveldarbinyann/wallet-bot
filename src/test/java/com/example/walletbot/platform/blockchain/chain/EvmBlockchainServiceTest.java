package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
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

@Timeout(20)
class EvmBlockchainServiceTest {

    private static final String CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
    private static final String TO = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String PK = "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";
    private static final int USDT_DECIMALS = 6;

    private JsonRpcStubServer rpc;

    @AfterEach
    void tearDown() {
        if (rpc != null) rpc.close();
    }

    private EvmBlockchainService service(boolean strictChecksum) {
        return new EvmBlockchainService(BlockchainNetwork.ETH, "http://localhost", strictChecksum, 1L);
    }

    private EvmBlockchainService liveService() throws IOException {
        rpc = new JsonRpcStubServer();
        return new EvmBlockchainService(BlockchainNetwork.ETH, rpc.url(), false, 1L);
    }

    @Test
    void isValidAddress_allLowercase_valid() {
        assertThat(service(false).isValidAddress("0x1234567890abcdef1234567890abcdef12345678")).isTrue();
    }

    @Test
    void isValidAddress_checksummedMixedCase_valid() {
        assertThat(service(true).isValidAddress("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")).isTrue();
    }

    @Test
    void isValidAddress_badChecksumMixedCase_invalid() {
        assertThat(service(true).isValidAddress("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1Beaed")).isFalse();
    }

    @Test
    void isValidAddress_allUppercaseRejectedUnderStrictChecksum() {
        String upper = "0x52908400098527886E0F7030069857D2E4169EE7";
        assertThat(service(true).isValidAddress(upper)).isFalse();
        assertThat(service(false).isValidAddress(upper)).isTrue();
    }

    @Test
    void isValidAddress_wrongLength_invalid() {
        assertThat(service(false).isValidAddress("0x1234")).isFalse();
    }

    @Test
    void isValidAddress_zeroAddress_invalid() {
        assertThat(service(false).isValidAddress("0x0000000000000000000000000000000000000000")).isFalse();
    }

    @Test
    void isValidAddress_missingPrefixOrNonHex_invalid() {
        assertThat(service(false).isValidAddress("1234567890abcdef1234567890abcdef12345678")).isFalse();
        assertThat(service(false).isValidAddress("0xZZZ4567890abcdef1234567890abcdef12345678")).isFalse();
        assertThat(service(false).isValidAddress(null)).isFalse();
    }

    @Test
    void getTokenBalance_decodesHexWordAndScalesByDecimals() throws IOException {
        EvmBlockchainService svc = liveService();
        rpc.on("eth_call", "0x" + Long.toHexString(1_500_000L));

        assertThat(svc.getTokenBalance(TO, CONTRACT, USDT_DECIMALS)).isEqualTo("1.5");
    }

    @Test
    void getTokenBalance_emptyResult_returnsZero() throws IOException {
        EvmBlockchainService svc = liveService();
        rpc.on("eth_call", "0x");

        assertThat(svc.getTokenBalance(TO, CONTRACT, USDT_DECIMALS)).isEqualTo("0.00");
    }

    @Test
    void getTokenBalance_rpcError_throws() throws IOException {
        EvmBlockchainService svc = liveService();
        assertThatThrownBy(() -> svc.getTokenBalance(TO, CONTRACT, USDT_DECIMALS))
                .isInstanceOf(BlockchainRpcException.class);
    }

    @Test
    void estimateTokenGasCost_multipliesBufferedGasPriceByLimit() throws IOException {
        EvmBlockchainService svc = liveService();
        rpc.on("eth_estimateGas", "0xfde8");
        rpc.on("eth_gasPrice", "0x3b9aca00");

        assertThat(svc.estimateTokenGasCost(CONTRACT)).isEqualByComparingTo("0.000078");
    }

    @Test
    void estimateTokenGasCost_gasPriceUnavailable_fallsBackToStaticFee() throws IOException {
        EvmBlockchainService svc = liveService();
        rpc.on("eth_estimateGas", "0xfde8");

        assertThat(svc.estimateTokenGasCost(CONTRACT)).isEqualByComparingTo("0.003");
    }

    @Test
    void sendToken_happyPath_signsAndReturnsTxHash() throws IOException {
        EvmBlockchainService svc = liveService();
        String txHash = "0x" + "ab".repeat(32);
        rpc.on("eth_call", "0x" + Long.toHexString(10_000_000L));
        rpc.on("eth_getBalance", "0xde0b6b3a7640000");
        rpc.on("eth_gasPrice", "0x1");
        rpc.on("eth_estimateGas", "0xfde8");
        rpc.on("eth_getTransactionCount", "0x0");
        rpc.on("eth_sendRawTransaction", txHash);

        assertThat(svc.sendToken(PK, TO, new BigDecimal("1"), CONTRACT, USDT_DECIMALS)).isEqualTo(txHash);
    }

    @Test
    void sendToken_insufficientTokenBalance_throws() throws IOException {
        EvmBlockchainService svc = liveService();
        rpc.on("eth_call", "0x1");

        assertThatThrownBy(() -> svc.sendToken(PK, TO, new BigDecimal("1"), CONTRACT, USDT_DECIMALS))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void sendToken_insufficientNativeForGas_throws() throws IOException {
        EvmBlockchainService svc = liveService();
        rpc.on("eth_call", "0x" + Long.toHexString(10_000_000L));
        rpc.on("eth_getBalance", "0x0");
        rpc.on("eth_gasPrice", "0x3b9aca00");
        rpc.on("eth_estimateGas", "0xfde8");

        assertThatThrownBy(() -> svc.sendToken(PK, TO, new BigDecimal("1"), CONTRACT, USDT_DECIMALS))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("gas");
    }

    private static final class JsonRpcStubServer implements AutoCloseable {
        private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

        private final HttpServer server;
        private final Map<String, String> results = new HashMap<>();

        JsonRpcStubServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] reqBytes = exchange.getRequestBody().readAllBytes();
                String req = new String(reqBytes, StandardCharsets.UTF_8);
                String method = group(METHOD, req);
                String id = group(ID, req);
                String result = results.get(method);
                String body = result != null
                        ? "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":\"" + result + "\"}"
                        : "{\"jsonrpc\":\"2.0\",\"id\":" + id
                          + ",\"error\":{\"code\":-32601,\"message\":\"no stub for " + method + "\"}}";
                byte[] out = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            });
            server.start();
        }

        private static String group(Pattern p, String s) {
            Matcher m = p.matcher(s);
            return m.find() ? m.group(1) : "1";
        }

        void on(String method, String resultValue) {
            results.put(method, resultValue);
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
