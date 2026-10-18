package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.swap.domain.exception.SwapProviderUnavailableException;
import com.example.walletbot.swap.provider.simpleswap.dto.SimpleSwapExchangeDto;
import com.example.walletbot.swap.provider.simpleswap.dto.SimpleSwapRangesDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SimpleSwapClientTest {

    private static final SimpleSwapCurrency FROM = new SimpleSwapCurrency("usdt", "eth");
    private static final SimpleSwapCurrency TO = new SimpleSwapCurrency("btc", "btc");

    private MockRestServiceServer server;
    private SimpleSwapClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SimpleSwapProperties props = new SimpleSwapProperties(
                "https://api.simpleswap.io", "test-key", true,
                60, 3000, 8000, 500,
                new BigDecimal("0.01"), new BigDecimal("0.02"), 10, true);
        client = new SimpleSwapClient(builder, props);
    }

    @Test
    void getRanges_unwrapsResultAndSendsApiKey() {
        server.expect(requestTo(containsString("/v3/ranges")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(queryParam("tickerFrom", "usdt"))
                .andExpect(queryParam("networkFrom", "eth"))
                .andExpect(queryParam("fixed", "false"))
                .andRespond(withSuccess("{\"result\":{\"min\":\"5\",\"max\":\"9000\"}}",
                        MediaType.APPLICATION_JSON));

        Optional<SimpleSwapRangesDto> ranges = client.getRanges(FROM, TO);

        assertThat(ranges).isPresent();
        assertThat(ranges.get().min()).isEqualTo("5");
        assertThat(ranges.get().max()).isEqualTo("9000");
        server.verify();
    }

    @Test
    void getEstimated_parsesEstimatedAmount() {
        server.expect(requestTo(containsString("/v3/estimates")))
                .andExpect(queryParam("amount", "10"))
                .andRespond(withSuccess("{\"result\":{\"estimatedAmount\":\"9.5\"}}",
                        MediaType.APPLICATION_JSON));

        Optional<BigDecimal> estimated = client.getEstimated(FROM, TO, new BigDecimal("10"));

        assertThat(estimated).isPresent();
        assertThat(estimated.get()).isEqualByComparingTo("9.5");
        server.verify();
    }

    @Test
    void getEstimated_nullEstimate_returnsEmpty() {
        server.expect(requestTo(containsString("/v3/estimates")))
                .andRespond(withSuccess("{\"result\":{\"estimatedAmount\":null}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getEstimated(FROM, TO, new BigDecimal("10"))).isEmpty();
        server.verify();
    }

    @Test
    void createExchange_postsBodyAndReturnsExchange() {
        server.expect(requestTo(containsString("/v3/exchanges")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.tickerFrom").value("usdt"))
                .andExpect(jsonPath("$.addressTo").value("destAddr"))
                .andExpect(jsonPath("$.userRefundAddress").value("refundAddr"))
                .andRespond(withSuccess(
                        "{\"result\":{\"id\":\"ex-1\",\"status\":\"waiting\",\"addressFrom\":\"deposit\"," +
                                "\"extraIdFrom\":\"memo-9\"}}",
                        MediaType.APPLICATION_JSON));

        SimpleSwapExchangeDto exchange = client.createExchange(
                FROM, TO, new BigDecimal("10"), "destAddr", "refundAddr");

        assertThat(exchange.id()).isEqualTo("ex-1");
        assertThat(exchange.addressFrom()).isEqualTo("deposit");
        assertThat(exchange.extraIdFrom()).isEqualTo("memo-9");
        server.verify();
    }

    @Test
    void createExchange_incompleteResponse_throwsProviderUnavailable() {
        server.expect(requestTo(containsString("/v3/exchanges")))
                .andRespond(withSuccess("{\"result\":{\"id\":\"ex-1\",\"status\":\"waiting\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createExchange(
                FROM, TO, new BigDecimal("10"), "destAddr", "refundAddr"))
                .isInstanceOf(SwapProviderUnavailableException.class);
        server.verify();
    }

    @Test
    void getExchange_unwrapsResult() {
        server.expect(requestTo(containsString("/v3/exchanges/ex-1")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"result\":{\"id\":\"ex-1\",\"status\":\"finished\"," +
                                "\"amountTo\":\"0.5\",\"txTo\":\"0xdest\"}}",
                        MediaType.APPLICATION_JSON));

        Optional<SimpleSwapExchangeDto> exchange = client.getExchange("ex-1");

        assertThat(exchange).isPresent();
        assertThat(exchange.get().status()).isEqualTo("finished");
        assertThat(exchange.get().txTo()).isEqualTo("0xdest");
        server.verify();
    }

    @Test
    void getExchange_404_returnsEmpty() {
        server.expect(requestTo(containsString("/v3/exchanges/missing")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThat(client.getExchange("missing")).isEmpty();
        server.verify();
    }

    @Test
    void confirmExchange_putFailure_isSwallowed() {
        server.expect(requestTo(containsString("/v3/exchanges")))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        client.confirmExchange("ex-1", "0xtx", "deposit", "destAddr");
        server.verify();
    }
}
