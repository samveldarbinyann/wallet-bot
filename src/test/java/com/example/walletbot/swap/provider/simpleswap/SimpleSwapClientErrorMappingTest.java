package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.swap.domain.exception.SwapProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SimpleSwapClientErrorMappingTest {

    private static final SimpleSwapCurrency FROM = new SimpleSwapCurrency("usdt", "eth");
    private static final SimpleSwapCurrency TO = new SimpleSwapCurrency("usdt", "trx");

    private MockRestServiceServer server;
    private SimpleSwapClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SimpleSwapProperties props = new SimpleSwapProperties(
                "https://api.simpleswap.io", "test-key", true,
                60, 3000, 8000, 500,
                new BigDecimal("0.01"), new BigDecimal("0.02"),
                10, true);
        client = new SimpleSwapClient(builder, props);
    }

    @Test
    void getRanges_429_throwsProviderUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/ranges")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":429,\"message\":\"too_many_requests\"}"));

        assertThatThrownBy(() -> client.getRanges(FROM, TO))
                .isInstanceOf(SwapProviderUnavailableException.class);

        server.verify();
    }

    @Test
    void getRanges_503_throwsProviderUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/ranges")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getRanges(FROM, TO))
                .isInstanceOf(SwapProviderUnavailableException.class);

        server.verify();
    }

    @Test
    void getRanges_400_returnsEmptyOptional() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/ranges")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"unsupported pair\"}"));

        assertThat(client.getRanges(FROM, TO)).isEmpty();

        server.verify();
    }

    @Test
    void getRanges_401_throwsProviderUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/ranges")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":401,\"message\":\"Wrong api key\"}"));

        assertThatThrownBy(() -> client.getRanges(FROM, TO))
                .isInstanceOf(SwapProviderUnavailableException.class);

        server.verify();
    }

    @Test
    void getEstimated_429_throwsProviderUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/estimates")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":429}"));

        assertThatThrownBy(() -> client.getEstimated(FROM, TO, new BigDecimal("10")))
                .isInstanceOf(SwapProviderUnavailableException.class);

        server.verify();
    }

    @Test
    void getEstimated_400_returnsEmptyOptional() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/estimates")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.getEstimated(FROM, TO, new BigDecimal("10"))).isEmpty();

        server.verify();
    }
}
