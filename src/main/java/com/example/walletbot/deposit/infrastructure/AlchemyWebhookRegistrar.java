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

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlchemyWebhookRegistrar {

    private static final String UPDATE_ADDRESSES_PATH = "/api/update-webhook-addresses";
    private static final String AUTH_TOKEN_HEADER = "X-Alchemy-Token";
    private static final String DEFAULT_DASHBOARD_URL = "https://dashboard.alchemy.com";

    private final RestTemplate restTemplate;
    private final AlchemyWebhookProperties properties;

    public boolean isConfigured() {
        return properties.isRegistrationConfigured();
    }

    public void addAddresses(List<String> addresses) {
        if (addresses.isEmpty() || !isConfigured()) {
            return;
        }
        addToWebhook(properties.eth(), addresses);
        addToWebhook(properties.bnb(), addresses);
    }

    private void addToWebhook(AlchemyWebhookProperties.Endpoint endpoint, List<String> addresses) {
        if (endpoint == null || endpoint.id() == null || endpoint.id().isBlank()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(AUTH_TOKEN_HEADER, properties.authToken());
            Map<String, Object> body = Map.of(
                    "webhook_id", endpoint.id(),
                    "addresses_to_add", addresses,
                    "addresses_to_remove", List.of());
            restTemplate.exchange(dashboardBase() + UPDATE_ADDRESSES_PATH, HttpMethod.PATCH,
                    new HttpEntity<>(body, headers), Void.class);
            log.info("Registered {} address(es) to Alchemy webhook {}", addresses.size(), endpoint.id());
        } catch (Exception e) {
            log.warn("Failed to register addresses to Alchemy webhook {}: {}", endpoint.id(), e.getMessage());
        }
    }

    private String dashboardBase() {
        return UrlUtils.baseUrlOrDefault(properties.dashboardUrl(), DEFAULT_DASHBOARD_URL);
    }
}