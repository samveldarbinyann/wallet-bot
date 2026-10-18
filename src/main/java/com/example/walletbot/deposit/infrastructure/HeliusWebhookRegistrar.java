package com.example.walletbot.deposit.infrastructure;

import com.example.walletbot.platform.blockchain.config.RpcProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.util.UrlUtils;
import com.example.walletbot.wallet.domain.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HeliusWebhookRegistrar {

    private static final String WEBHOOKS_PATH = "/v0/webhooks/";
    private static final String DEFAULT_MANAGEMENT_URL = "https://api.helius.xyz";

    private final RestTemplate restTemplate;
    private final HeliusWebhookProperties properties;
    private final WalletRepository walletRepository;
    private final String apiKey;

    public HeliusWebhookRegistrar(RestTemplate restTemplate, HeliusWebhookProperties properties,
                                  WalletRepository walletRepository, RpcProperties rpc) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.walletRepository = walletRepository;
        this.apiKey = rpc.sol().key();
    }

    public boolean isConfigured() {
        return properties.isConfigured() && apiKey != null && !apiKey.isBlank();
    }

    public synchronized void syncAddresses() {
        if (!isConfigured()) {
            return;
        }
        List<String> addresses = walletRepository.findAddressesByNetwork(BlockchainNetwork.SOL);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of("accountAddresses", addresses);
            restTemplate.exchange(webhookUrl(), HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
            log.info("Synced {} Solana address(es) to Helius webhook {}", addresses.size(), properties.id());
        } catch (Exception e) {
            log.warn("Failed to sync addresses to Helius webhook {}: {}", properties.id(), e.getMessage());
        }
    }

    private String webhookUrl() {
        return managementBase() + WEBHOOKS_PATH + properties.id() + "?api-key=" + apiKey;
    }

    private String managementBase() {
        return UrlUtils.baseUrlOrDefault(properties.managementUrl(), DEFAULT_MANAGEMENT_URL);
    }
}
