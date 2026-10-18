package com.example.walletbot.platform.blockchain.client;

import com.example.walletbot.platform.blockchain.ratelimit.TonCenterRateLimiter;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.util.HexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Slf4j
public class TonCenterClient {

    private static final long NANO_PER_TON = 1_000_000_000L;

    private final RestTemplate restTemplate;
    private final String tonCenterUrl;
    private final String tonCenterApiKey;
    private final TonCenterRateLimiter rateLimiter;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF =
            new ParameterizedTypeReference<>() {
            };

    public TonCenterClient(RestTemplate restTemplate, String tonCenterUrl, String tonCenterApiKey,
                           TonCenterRateLimiter rateLimiter) {
        if (restTemplate == null) throw new IllegalArgumentException("restTemplate must not be null");
        if (tonCenterUrl == null || tonCenterUrl.isBlank()) {
            throw new IllegalArgumentException("rpc.ton.url must be set in application.yaml");
        }
        this.restTemplate = restTemplate;
        this.tonCenterUrl = tonCenterUrl.trim();
        this.tonCenterApiKey = (tonCenterApiKey != null && !tonCenterApiKey.isBlank())
                ? tonCenterApiKey.trim() : null;
        this.rateLimiter = rateLimiter;
    }

    public Map<String, Object> get(String pathAndQuery) {
        rateLimiter.acquire();
        return restTemplate.exchange(
                buildUrl(pathAndQuery),
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders(false)),
                MAP_TYPE_REF
        ).getBody();
    }

    public Map<String, Object> postJson(String path, Map<String, ?> body) {
        rateLimiter.acquire();
        return restTemplate.exchange(
                buildUrl(path),
                HttpMethod.POST,
                new HttpEntity<>(body, buildHeaders(true)),
                MAP_TYPE_REF
        ).getBody();
    }

    public BigDecimal getBalanceTon(String address) {
        Map<String, Object> response;
        try {
            response = get("getAddressBalance?address=" + address);
        } catch (Exception e) {
            log.error("TON Balance error: ", e);
            throw new BlockchainRpcException("TonCenter getAddressBalance failed for " + address);
        }
        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
            throw new BlockchainRpcException("TonCenter getAddressBalance failed for " + address);
        }
        String nanotons = response.get("result").toString();
        return new BigDecimal(nanotons)
                .divide(BigDecimal.valueOf(NANO_PER_TON), 9, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    public WalletInformation getWalletInformation(String address) {
        Map<String, Object> response = get("getWalletInformation?address=" + address);
        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
            throw new BlockchainRpcException("TonCenter getWalletInformation failed for " + address);
        }
        Object resultObj = response.get("result");
        if (!(resultObj instanceof Map)) {
            throw new BlockchainRpcException("TonCenter getWalletInformation unexpected result shape");
        }
        Map<String, Object> result = (Map<String, Object>) resultObj;
        String state = result.get("account_state") != null ? result.get("account_state").toString() : null;
        Object seqnoObj = result.get("seqno");
        Long seqno = (seqnoObj instanceof Number n) ? n.longValue() : null;
        return new WalletInformation(state, seqno);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTransactions(String address, int limit) {
        Map<String, Object> response = get("getTransactions?address=" + address + "&limit=" + limit);
        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) return List.of();
        return response.get("result") instanceof List<?> txs
                ? (List<Map<String, Object>>) txs
                : List.of();
    }

    @SuppressWarnings("unchecked")
    public TonTxInfo findTransactionByInMessageHash(String address, String msgHashBase64) {
        try {
            for (Map<String, Object> txMap : getTransactions(address, 100)) {
                Map<String, Object> inMsg = (Map<String, Object>) txMap.get("in_msg");
                if (inMsg == null) continue;
                Object hash = inMsg.get("hash");
                if (hash == null || !hash.toString().equals(msgHashBase64)) continue;

                long utime = txMap.get("utime") instanceof Number n ? n.longValue() : 0L;
                BigDecimal feeTon = txMap.get("fee") instanceof String feeStr
                        ? new BigDecimal(feeStr).divide(BigDecimal.valueOf(NANO_PER_TON), 9, RoundingMode.HALF_UP)
                        : null;
                return new TonTxInfo(utime, feeTon);
            }
            return null;
        } catch (Exception e) {
            log.warn("TON findTransactionByInMessageHash error for {}: {}", address, e.getMessage());
            return null;
        }
    }

    public record TonTxInfo(long utime, BigDecimal feeTon) {
    }

    public String sendBocReturnHash(String bocBase64) {
        try {
            Map<String, Object> response = postJson("sendBocReturnHash", Map.of("boc", bocBase64));
            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                Object result = response.get("result");
                if (result instanceof Map<?, ?> m) {
                    Object hashObj = m.get("hash");
                    if (hashObj instanceof String hash) return hash;
                }
            }
        } catch (Exception e) {
            log.error("TON sendBocReturnHash error: ", e);
            throw new BlockchainRpcException("TonCenter sendBocReturnHash failed: " + e.getMessage());
        }
        throw new BlockchainRpcException("TonCenter sendBocReturnHash: no hash in response");
    }

    public BigInteger runGetMethodForBalance(String jettonWalletAddress) {
        Object value = runGetMethodFirstStackValue(Map.of(
                "address", jettonWalletAddress,
                "method", "get_wallet_data",
                "stack", List.of()));
        if (value == null) return null;
        return new BigInteger(HexUtils.stripHexPrefix(value.toString()), 16);
    }

    public String runGetMethodForJettonWalletAddress(String jettonMasterAddress, String ownerAddressBocBase64) {
        Object value = runGetMethodFirstStackValue(Map.of(
                "address", jettonMasterAddress,
                "method", "get_wallet_address",
                "stack", List.of(List.of("tvm.Slice", ownerAddressBocBase64))));
        return value == null ? null : extractStackCellBoc(value);
    }

    private Object runGetMethodFirstStackValue(Map<String, ?> body) {
        Map<String, Object> response = postJson("runGetMethod", body);
        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) return null;
        if (!(response.get("result") instanceof Map<?, ?> result)) return null;
        if (!(result.get("exit_code") instanceof Number ec) || ec.intValue() != 0) return null;
        if (!(result.get("stack") instanceof List<?> stack) || stack.isEmpty()) return null;
        if (!(stack.getFirst() instanceof List<?> first) || first.size() < 2) return null;
        return first.get(1);
    }

    private String extractStackCellBoc(Object stackItem) {
        if (stackItem instanceof Map<?, ?> m) {
            Object bytes = m.get("bytes");
            return bytes != null ? bytes.toString() : null;
        }
        String s = stackItem != null ? stackItem.toString() : null;
        return (s != null && !s.isBlank()) ? s : null;
    }

    private String buildUrl(String path) {
        String base = tonCenterUrl.endsWith("/") ? tonCenterUrl : tonCenterUrl + "/";
        return base + (path.startsWith("/") ? path.substring(1) : path);
    }

    private HttpHeaders buildHeaders(boolean withContentType) {
        HttpHeaders headers = new HttpHeaders();
        if (withContentType) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (tonCenterApiKey != null) {
            headers.set("X-API-Key", tonCenterApiKey);
        }
        return headers;
    }

    public record WalletInformation(String accountState, Long seqno) {
        public boolean isUninitialized() {
            return "uninitialized".equals(accountState)
                    || "uninit".equals(accountState)
                    || "nonexist".equals(accountState);
        }
    }
}