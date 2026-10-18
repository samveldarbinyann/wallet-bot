package com.example.walletbot.platform.blockchain.client;

import com.example.walletbot.platform.blockchain.ratelimit.TronGridRateLimiter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


public class TronGridClient {

    public static final String TRON_PRO_API_KEY_HEADER = "TRON-PRO-API-KEY";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;
    private final TronGridRateLimiter rateLimiter;

    public TronGridClient(RestTemplate restTemplate, String apiUrl, String apiKey,
                          TronGridRateLimiter rateLimiter) {
        if (restTemplate == null) throw new IllegalArgumentException("restTemplate must not be null");
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException("rpc.tron.url must be set in application.yaml");
        }
        if (rateLimiter == null) throw new IllegalArgumentException("rateLimiter must not be null");
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl.trim();
        this.apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey.trim() : null;
        this.rateLimiter = rateLimiter;
    }

    private HttpHeaders buildHeaders(boolean withContentType) {
        HttpHeaders headers = new HttpHeaders();
        if (withContentType) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (apiKey != null) {
            headers.set(TRON_PRO_API_KEY_HEADER, apiKey);
        }
        return headers;
    }

    private Map<String, Object> get(String url) {
        rateLimiter.acquire();
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(buildHeaders(false)), MAP_TYPE);
        return response.getBody();
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        rateLimiter.acquire();
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                apiUrl + path, HttpMethod.POST, new HttpEntity<>(body, buildHeaders(true)), MAP_TYPE);
        return response.getBody();
    }

    public Map<String, Object> getV1Account(String address) {
        return get(apiUrl + "/v1/accounts/" + address);
    }

    public Map<String, Object> getAccountTransactions(String address, boolean onlyTo, int limit) {
        return get(apiUrl + "/v1/accounts/" + address + "/transactions?only_to=" + onlyTo + "&limit=" + limit);
    }

    public Map<String, Object> getTrc20Transactions(String address, boolean onlyTo, int limit) {
        return get(apiUrl + "/v1/accounts/" + address + "/transactions/trc20?only_to=" + onlyTo + "&limit=" + limit);
    }

    public Map<String, Object> walletCreateTransaction(Map<String, Object> body) {
        return post("/wallet/createtransaction", body);
    }

    public Map<String, Object> walletBroadcastTransaction(Map<String, Object> signedTx) {
        return post("/wallet/broadcasttransaction", signedTx);
    }

    public Map<String, Object> walletTriggerSmartContract(Map<String, Object> body) {
        return post("/wallet/triggersmartcontract", body);
    }

    public Map<String, Object> walletTriggerConstantContract(Map<String, Object> body) {
        return post("/wallet/triggerconstantcontract", body);
    }

    public Map<String, Object> walletGetAccountResource(String address) {
        return post("/wallet/getaccountresource", Map.of("address", address, "visible", true));
    }

    public Map<String, Object> walletGetTransactionInfoById(String txId) {
        return post("/wallet/gettransactioninfobyid", Map.of("value", txId));
    }
}