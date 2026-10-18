package com.example.walletbot.platform.blockchain.client;

import com.example.walletbot.shared.exception.BlockchainRpcException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
public class SolRpcClient {

    private static final long DEFAULT_PRIORITY_FEE_MICRO_LAMPORTS = 1_000L;

    private static final long DEFAULT_CONFIRM_POLL_MS = 2_500L;
    private static final int DEFAULT_CONFIRM_ATTEMPTS = 10;

    private final RestTemplate restTemplate;
    private final String rpcUrl;
    private final long confirmPollMs;
    private final int confirmAttempts;

    public SolRpcClient(RestTemplate restTemplate, String rpcUrl) {
        this(restTemplate, rpcUrl, DEFAULT_CONFIRM_POLL_MS, DEFAULT_CONFIRM_ATTEMPTS);
    }

    public SolRpcClient(RestTemplate restTemplate, String rpcUrl, long confirmPollMs, int confirmAttempts) {
        if (restTemplate == null) throw new IllegalArgumentException("restTemplate must not be null");
        if (rpcUrl == null || rpcUrl.isBlank()) {
            throw new IllegalArgumentException("rpc.sol.url must be set in application.yaml");
        }
        this.restTemplate = restTemplate;
        this.rpcUrl = rpcUrl.trim();
        this.confirmPollMs = confirmPollMs;
        this.confirmAttempts = confirmAttempts;
    }

    public String getRpcUrl() {
        return rpcUrl;
    }

    @SuppressWarnings("unchecked")
    public String getLatestBlockhash() {
        Map<String, Object> payload = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "getLatestBlockhash",
                "params", List.of(Map.of("commitment", "confirmed"))
        );
        try {
            Map<String, Object> response = restTemplate.postForObject(rpcUrl, payload, Map.class);
            if (response != null) {
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                if (result != null) {
                    Map<String, Object> value = (Map<String, Object>) result.get("value");
                    if (value != null) {
                        return (String) value.get("blockhash");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to retrieve SOL blockhash: ", e);
        }
        throw new BlockchainRpcException("Failed to retrieve current blockhash from SOL RPC");
    }

    @SuppressWarnings("unchecked")
    public SignatureStatus getSignatureStatus(String signature) {
        Map<String, Object> payload = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "getSignatureStatuses",
                "params", List.of(List.of(signature), Map.of("searchTransactionHistory", true))
        );
        try {
            Map<String, Object> response = restTemplate.postForObject(rpcUrl, payload, Map.class);
            if (response == null) return null;
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            if (result == null) return null;
            List<Map<String, Object>> values = (List<Map<String, Object>>) result.get("value");
            if (values == null || values.isEmpty() || values.getFirst() == null) return null;
            Map<String, Object> v = values.getFirst();
            String confirmation = (String) v.get("confirmationStatus");
            Object err = v.get("err");
            Long slot = v.get("slot") instanceof Number n ? n.longValue() : null;
            return new SignatureStatus(confirmation, err, slot);
        } catch (Exception e) {
            log.warn("SOL getSignatureStatuses error for {}: {}", signature, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Long getBlockTime(long slot) {
        Map<String, Object> payload = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "getBlockTime",
                "params", List.of(slot)
        );
        try {
            Map<String, Object> response = restTemplate.postForObject(rpcUrl, payload, Map.class);
            if (response == null) return null;
            Object result = response.get("result");
            return result instanceof Number n ? n.longValue() : null;
        } catch (Exception e) {
            log.debug("SOL getBlockTime error for slot {}: {}", slot, e.getMessage());
            return null;
        }
    }

    public record SignatureStatus(String confirmationStatus, Object err, Long slot) {
        public boolean isFinalized() {
            return "finalized".equals(confirmationStatus) || "confirmed".equals(confirmationStatus);
        }

        public boolean isErrored() {
            return err != null;
        }
    }

    @SuppressWarnings("unchecked")
    public long getPriorityFeeEstimate() {
        Map<String, Object> payload = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "getPriorityFeeEstimate",
                "params", List.of(Map.of("options", Map.of("priorityLevel", "High")))
        );
        try {
            Map<String, Object> response = restTemplate.postForObject(rpcUrl, payload, Map.class);
            if (response != null) {
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                if (result != null) {
                    Number fee = (Number) result.get("priorityFeeEstimate");
                    if (fee != null) return fee.longValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve SOL priority fee, fallback: {}", e.getMessage());
        }
        return DEFAULT_PRIORITY_FEE_MICRO_LAMPORTS;
    }

    @SuppressWarnings("unchecked")
    public String sendRawTransaction(String base64Tx) {
        Map<String, Object> payload = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "sendTransaction",
                "params", List.of(base64Tx, Map.of(
                        "encoding", "base64",
                        "skipPreflight", true,
                        "maxRetries", 3,
                        "preflightCommitment", "confirmed"))
        );
        try {
            Map<String, Object> response = restTemplate.postForObject(rpcUrl, payload, Map.class);
            if (response != null) {
                Object result = response.get("result");
                if (result instanceof String sig && !sig.isBlank()) {
                    return sig;
                }
                Object error = response.get("error");
                if (error != null) {
                    throw new BlockchainRpcException("SOL sendTransaction error: " + error);
                }
            }
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            throw new BlockchainRpcException("SOL sendTransaction failed: " + e.getMessage());
        }
        throw new BlockchainRpcException("SOL sendTransaction returned no signature");
    }

    public void confirmTransaction(String signature, String rawTxBase64) {
        for (int attempt = 0; attempt < confirmAttempts; attempt++) {
            SignatureStatus status = getSignatureStatus(signature);
            if (status != null) {
                if (status.isErrored()) {
                    throw new BlockchainRpcException("SOL transaction reverted on-chain: " + signature);
                }
                if (status.isFinalized()) {
                    return;
                }
            }
            try {
                Thread.sleep(confirmPollMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BlockchainRpcException("Interrupted while confirming SOL transaction " + signature);
            }
            try {
                sendRawTransaction(rawTxBase64);
            } catch (Exception e) {
                log.debug("SOL rebroadcast attempt {} for {} failed: {}", attempt + 1, signature, e.getMessage());
            }
        }
        SignatureStatus status = getSignatureStatus(signature);
        if (status != null && status.isFinalized() && !status.isErrored()) {
            return;
        }
        throw new BlockchainRpcException(
                "SOL transaction not confirmed within timeout (likely dropped): " + signature);
    }
}