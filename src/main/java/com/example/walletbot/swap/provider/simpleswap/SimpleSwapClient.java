package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.swap.domain.exception.SwapProviderUnavailableException;
import com.example.walletbot.swap.provider.simpleswap.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class SimpleSwapClient {

    private static final long THROTTLE_WARN_INTERVAL_SECONDS = 30;

    private static final ParameterizedTypeReference<SimpleSwapResponse<SimpleSwapRangesDto>> RANGES_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<SimpleSwapResponse<SimpleSwapEstimateDto>> ESTIMATE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<SimpleSwapResponse<SimpleSwapExchangeDto>> EXCHANGE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient client;
    private final AtomicReference<Instant> lastThrottleWarn = new AtomicReference<>(Instant.EPOCH);

    public SimpleSwapClient(RestClient.Builder builder, SimpleSwapProperties props) {
        this.client = builder
                .baseUrl(props.url())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", props.key() == null ? "" : props.key())
                .build();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private void logThrottled(String op, HttpClientErrorException e) {
        Instant now = Instant.now();
        Instant last = lastThrottleWarn.get();
        if (Duration.between(last, now).getSeconds() >= THROTTLE_WARN_INTERVAL_SECONDS
                && lastThrottleWarn.compareAndSet(last, now)) {
            log.warn("SimpleSwap v3 {} throttled (HTTP {}): {}", op, e.getStatusCode().value(),
                    truncate(e.getResponseBodyAsString()));
        } else {
            log.debug("SimpleSwap v3 {} throttled (HTTP {})", op, e.getStatusCode().value());
        }
    }

    private boolean isAuthFailure(HttpClientErrorException e) {
        int s = e.getStatusCode().value();
        return s == 401 || s == 403;
    }

    private SwapProviderUnavailableException authFailure(String op, HttpClientErrorException e) {
        log.error("SimpleSwap v3 {} rejected the API key (HTTP {}) — check SIMPLESWAP_KEY: {}",
                op, e.getStatusCode().value(), truncate(e.getResponseBodyAsString()));
        return new SwapProviderUnavailableException("SimpleSwap API key rejected");
    }

    public Optional<SimpleSwapRangesDto> getRanges(SimpleSwapCurrency from, SimpleSwapCurrency to) {
        try {
            SimpleSwapResponse<SimpleSwapRangesDto> response = client.get()
                    .uri(uri -> uri.path("/v3/ranges")
                            .queryParam("fixed", false)
                            .queryParam("tickerFrom", from.ticker())
                            .queryParam("networkFrom", from.network())
                            .queryParam("tickerTo", to.ticker())
                            .queryParam("networkTo", to.network())
                            .build())
                    .retrieve()
                    .body(RANGES_TYPE);
            return Optional.ofNullable(response).map(SimpleSwapResponse::result);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                logThrottled("ranges", e);
                throw new SwapProviderUnavailableException("SimpleSwap throttled");
            }
            if (isAuthFailure(e)) throw authFailure("ranges", e);
            log.debug("SimpleSwap v3 ranges 4xx: status={} body={}", e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("SimpleSwap v3 ranges failed: {}", e.getMessage());
            throw new SwapProviderUnavailableException("SimpleSwap ranges unavailable", e);
        }
    }

    public Optional<BigDecimal> getEstimated(SimpleSwapCurrency from, SimpleSwapCurrency to,
                                             BigDecimal amount) {
        try {
            SimpleSwapResponse<SimpleSwapEstimateDto> response = client.get()
                    .uri(uri -> uri.path("/v3/estimates")
                            .queryParam("fixed", false)
                            .queryParam("reverse", false)
                            .queryParam("tickerFrom", from.ticker())
                            .queryParam("networkFrom", from.network())
                            .queryParam("tickerTo", to.ticker())
                            .queryParam("networkTo", to.network())
                            .queryParam("amount", amount.toPlainString())
                            .build())
                    .retrieve()
                    .body(ESTIMATE_TYPE);
            if (response == null || response.result() == null
                    || response.result().estimatedAmount() == null) {
                return Optional.empty();
            }
            return Optional.of(new BigDecimal(response.result().estimatedAmount()));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                logThrottled("estimates", e);
                throw new SwapProviderUnavailableException("SimpleSwap throttled");
            }
            if (isAuthFailure(e)) throw authFailure("estimates", e);
            log.debug("SimpleSwap v3 estimates 4xx: status={} body={}", e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            return Optional.empty();
        } catch (RestClientException | NumberFormatException e) {
            log.error("SimpleSwap v3 estimates failed: {}", e.getMessage());
            throw new SwapProviderUnavailableException("SimpleSwap estimate unavailable", e);
        }
    }

    public SimpleSwapExchangeDto createExchange(SimpleSwapCurrency from, SimpleSwapCurrency to,
                                                BigDecimal amount, String addressTo,
                                                String refundAddress) {
        try {
            SimpleSwapCreateExchangeRequest req = new SimpleSwapCreateExchangeRequest(
                    false,
                    from.ticker(), from.network(),
                    to.ticker(), to.network(),
                    amount.toPlainString(),
                    addressTo,
                    refundAddress);
            SimpleSwapResponse<SimpleSwapExchangeDto> response = client.post()
                    .uri("/v3/exchanges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(EXCHANGE_TYPE);
            SimpleSwapExchangeDto exchange = response == null ? null : response.result();
            if (exchange == null || exchange.id() == null || exchange.addressFrom() == null) {
                throw new SwapProviderUnavailableException(
                        "SimpleSwap returned an incomplete create-exchange response");
            }
            return exchange;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                logThrottled("create-exchange", e);
                throw new SwapProviderUnavailableException("SimpleSwap throttled");
            }
            if (isAuthFailure(e)) throw authFailure("create-exchange", e);
            log.warn("SimpleSwap v3 create-exchange 4xx: status={} body={}", e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            throw new SwapProviderUnavailableException("SimpleSwap rejected exchange creation");
        } catch (RestClientException e) {
            log.error("SimpleSwap v3 create-exchange failed: {}", e.getMessage());
            throw new SwapProviderUnavailableException("SimpleSwap create-exchange unavailable", e);
        }
    }

    public Optional<SimpleSwapExchangeDto> getExchange(String publicId) {
        try {
            SimpleSwapResponse<SimpleSwapExchangeDto> response = client.get()
                    .uri("/v3/exchanges/{publicId}", publicId)
                    .retrieve()
                    .body(EXCHANGE_TYPE);
            return Optional.ofNullable(response).map(SimpleSwapResponse::result);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                logThrottled("get-exchange", e);
                throw new SwapProviderUnavailableException("SimpleSwap throttled");
            }
            if (isAuthFailure(e)) throw authFailure("get-exchange", e);
            log.debug("SimpleSwap v3 get-exchange 4xx for {}: status={}", publicId, e.getStatusCode());
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("SimpleSwap v3 get-exchange failed: {}", e.getMessage());
            throw new SwapProviderUnavailableException("SimpleSwap get-exchange unavailable", e);
        }
    }

    public void confirmExchange(String publicId, String txFrom, String depositAddress,
                                String targetAddress) {
        try {
            client.put()
                    .uri("/v3/exchanges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SimpleSwapConfirmExchangeRequest(
                            publicId, txFrom, depositAddress, targetAddress))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("SimpleSwap v3 confirm-exchange {} failed (deposit already on-chain): {}",
                    publicId, e.getMessage());
        }
    }
}
