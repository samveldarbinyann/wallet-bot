package com.example.walletbot.deposit.infrastructure;

import com.example.walletbot.shared.util.UrlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.ton.java.address.Address;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class TonApiWebhookRegistrar {

    private static final String SUBSCRIBE_PATH = "/webhooks/%s/account-tx/subscribe";
    private static final String DEFAULT_MANAGEMENT_URL = "https://rt.tonapi.io";

    private final RestTemplate restTemplate;
    private final TonApiWebhookProperties properties;

    private static String toRawAccountId(String address) {
        try {
            return Address.of(address.trim()).toRaw();
        } catch (Exception | Error e) {
            return null;
        }
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public void subscribe(List<String> addresses) {
        if (addresses.isEmpty() || !isConfigured()) {
            return;
        }
        List<Map<String, String>> accounts = addresses.stream()
                .map(TonApiWebhookRegistrar::toRawAccountId)
                .filter(Objects::nonNull)
                .map(id -> Map.of("account_id", id))
                .toList();
        if (accounts.isEmpty()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(properties.token());
            Map<String, Object> body = Map.of("accounts", accounts);
            restTemplate.exchange(subscribeUrl(), HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
            log.info("Subscribed {} TON address(es) to TonAPI webhook {}", accounts.size(), properties.id());
        } catch (Exception e) {
            log.warn("Failed to subscribe addresses to TonAPI webhook {}: {}", properties.id(), e.getMessage());
        }
    }

    private String subscribeUrl() {
        return managementBase() + SUBSCRIBE_PATH.formatted(properties.id());
    }

    private String managementBase() {
        return UrlUtils.baseUrlOrDefault(properties.managementUrl(), DEFAULT_MANAGEMENT_URL);
    }
}
