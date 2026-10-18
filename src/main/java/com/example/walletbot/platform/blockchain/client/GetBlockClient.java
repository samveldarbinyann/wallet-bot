package com.example.walletbot.platform.blockchain.client;

import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.util.HexUtils;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class GetBlockClient {

    private final RestTemplate restTemplate;
    private final String nodeUrl;
    private final String mempoolBaseUrl;
    private final AtomicInteger rpcId = new AtomicInteger(1);

    public GetBlockClient(RestTemplate restTemplate, String nodeUrl, String mempoolBaseUrl) {
        if (restTemplate == null) throw new IllegalArgumentException("restTemplate must not be null");
        if (nodeUrl == null || nodeUrl.isBlank())
            throw new IllegalArgumentException("rpc.btc.url must be set in application.yaml");
        this.restTemplate = restTemplate;
        String url = nodeUrl.trim();
        this.nodeUrl = url.endsWith("/") ? url : url + "/";
        String base = mempoolBaseUrl != null ? mempoolBaseUrl.trim() : "https://mempool.space/api";
        this.mempoolBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @SuppressWarnings("unchecked")
    public BigDecimal getBtcBalance(String address) {
        try {
            Map<String, Object> info = restTemplate.exchange(
                    mempoolBaseUrl + "/address/" + address,
                    HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()),
                    Map.class
            ).getBody();
            if (info == null) return BigDecimal.ZERO;
            Map<String, Object> chain = (Map<String, Object>) info.get("chain_stats");
            if (chain == null) return BigDecimal.ZERO;
            long funded = toLong(chain.get("funded_txo_sum"));
            long spent = toLong(chain.get("spent_txo_sum"));
            return BigDecimal.valueOf(Math.max(0L, funded - spent)).movePointLeft(8);
        } catch (Exception e) {
            log.error("[BTC-bal] mempool.space balance error for {}: ", address, e);
            return BigDecimal.ZERO;
        }
    }

    @SuppressWarnings("unchecked")
    public List<ParsedUtxo> getBtcUtxos(String address) {
        String url = mempoolBaseUrl + "/address/" + address + "/utxo";
        try {
            List<Map<String, Object>> list = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()), List.class
            ).getBody();
            if (list == null || list.isEmpty()) return List.of();

            List<ParsedUtxo> result = new ArrayList<>(list.size());
            for (Map<String, Object> item : list) {
                if (!isConfirmed(item)) continue;
                String txid = (String) item.get("txid");
                if (txid == null) continue;
                long vout = toLong(item.get("vout"));
                long value = toLong(item.get("value"));
                if (value <= 0) continue;
                if (!utxoIsUnspent(txid, vout)) continue;
                result.add(new ParsedUtxo(hashFromDisplayTxid(txid), vout, Coin.valueOf(value)));
            }
            log.debug("[BTC-utxo] {} spendable UTXOs for {}", result.size(), address);
            return result;
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("[BTC-utxo] UTXO fetch error for {}: ", address, e);
            throw new BlockchainRpcException("BTC UTXO fetch failed: " + e.getMessage());
        }
    }

    private boolean isConfirmed(Map<String, Object> item) {
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) item.get("status");
        return status != null && Boolean.TRUE.equals(status.get("confirmed"));
    }

    private boolean utxoIsUnspent(String txid, long vout) {
        try {
            Map<String, Object> resp = rpcCall("gettxout", List.of(txid, vout, false));
            if (resp == null) {
                log.debug("[BTC-utxo-check] {}:{} - null RPC response, skipping", txid, vout);
                return false;
            }
            Object error = resp.get("error");
            if (error != null) {
                log.debug("[BTC-utxo-check] {}:{} - RPC error {}, skipping", txid, vout, error);
                return false;
            }
            Object result = resp.get("result");
            if (result == null) {
                log.debug("[BTC-utxo-check] {}:{} - already spent (gettxout returned null)", txid, vout);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[BTC-utxo-check] {}:{} check error ({}) - allowing UTXO", txid, vout, e.getMessage());
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    public BtcTxStatus getBtcTxStatus(String txHash) {
        try {
            Map<String, Object> body = restTemplate.exchange(
                    mempoolBaseUrl + "/tx/" + txHash + "/status",
                    HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()),
                    Map.class
            ).getBody();
            if (body == null) return null;
            boolean confirmed = Boolean.TRUE.equals(body.get("confirmed"));
            Long blockTime = body.get("block_time") instanceof Number n ? n.longValue() : null;
            Long fee = null;
            if (confirmed) {
                try {
                    Map<String, Object> tx = restTemplate.exchange(
                            mempoolBaseUrl + "/tx/" + txHash,
                            HttpMethod.GET,
                            new HttpEntity<>(jsonHeaders()),
                            Map.class
                    ).getBody();
                    if (tx != null && tx.get("fee") instanceof Number n) {
                        fee = n.longValue();
                    }
                } catch (Exception ignored) {
                }
            }
            return new BtcTxStatus(confirmed, blockTime, fee);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            log.warn("[BTC-status] mempool.space {} status fetch error: {}", txHash, e.getStatusCode());
            return null;
        } catch (Exception e) {
            log.warn("[BTC-status] mempool.space {} status fetch failed: {}", txHash, e.getMessage());
            return null;
        }
    }

    public record BtcTxStatus(boolean confirmed, Long blockTime, Long feeSatoshi) {
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRecommendedFeePerByte() {
        return restTemplate.exchange(
                mempoolBaseUrl + "/v1/fees/recommended",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class
        ).getBody();
    }

    public String broadcastBtcViaMempoolSpace(String rawTxHex) {
        String txData = HexUtils.stripHexPrefix(rawTxHex);
        log.debug("[BTC-mp-broadcast] first 64 chars: {}",
                txData.substring(0, Math.min(64, txData.length())));
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.TEXT_PLAIN);
            String txId = restTemplate.exchange(
                    mempoolBaseUrl + "/tx",
                    HttpMethod.POST,
                    new HttpEntity<>(txData, h),
                    String.class
            ).getBody();
            if (txId != null && !txId.isBlank()) {
                log.info("[BTC-mp-broadcast] OK txId={}", txId.trim());
                return txId.trim();
            }
            throw new BlockchainRpcException("mempool.space: empty txId in response");
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("[BTC-mp-broadcast] HTTP {} body={}", e.getStatusCode(), body);
            throw new BlockchainRpcException("mempool.space broadcast failed: " + body);
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("[BTC-mp-broadcast] error: ", e);
            throw new BlockchainRpcException("mempool.space broadcast failed: " + e.getMessage());
        }
    }

    public String broadcastBtcViaGetBlock(String rawTxHex) {
        String txData = HexUtils.stripHexPrefix(rawTxHex);
        log.debug("[BTC-gb-broadcast] first 64 chars: {}",
                txData.substring(0, Math.min(64, txData.length())));
        try {
            Map<String, Object> resp = rpcCall("sendrawtransaction", List.of(txData));
            if (resp == null)
                throw new BlockchainRpcException("GetBlock: null response");
            Object error = resp.get("error");
            if (error != null) {
                log.error("[BTC-gb-broadcast] RPC error: {}", error);
                throw new BlockchainRpcException("GetBlock broadcast error: " + error);
            }
            Object result = resp.get("result");
            if (result instanceof String txId && !txId.isBlank()) {
                log.info("[BTC-gb-broadcast] OK txId={}", txId);
                return txId;
            }
            throw new BlockchainRpcException("GetBlock: no txId in response");
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("[BTC-gb-broadcast] error: ", e);
            throw new BlockchainRpcException("GetBlock broadcast failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rpcCall(String method, List<?> params) {
        Map<String, Object> body = Map.of(
                "jsonrpc", "2.0",
                "method", method,
                "params", params,
                "id", rpcId.getAndIncrement()
        );
        return restTemplate.exchange(
                nodeUrl, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                Map.class
        ).getBody();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    public static Sha256Hash hashFromDisplayTxid(String displayTxid) {
        return Sha256Hash.wrap(Numeric.hexStringToByteArray(HexUtils.stripHexPrefix(displayTxid)));
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    public record ParsedUtxo(Sha256Hash hash, long index, Coin value) {
    }
}