package com.example.walletbot.swap.api.mapper;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.api.dto.*;
import com.example.walletbot.swap.application.SwapQuoteService;
import com.example.walletbot.swap.application.intent.SwapIntentService;
import com.example.walletbot.swap.domain.*;
import com.example.walletbot.transaction.domain.NetworkFee;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SwapApiMapperTest {

    private final SwapApiMapper mapper = new SwapApiMapper();

    private SwapQuote quote(Map<String, String> meta) {
        return new SwapQuote("simpleswap", "pq", SwapPair.of(BlockchainNetwork.USDT_TRC20, BlockchainNetwork.SOL),
                new BigDecimal("100"), new BigDecimal("98"), new BigDecimal("96"), new BigDecimal("0.98"),
                new SwapFeeBreakdown(new BigDecimal("0.01"), new BigDecimal("0.98")),
                Instant.now().plusSeconds(60), meta);
    }

    private SwapQuoteService.QuotedSwap quoted(SwapQuote q, SwapIntentService.IntentToken token,
                                               NetworkFee networkFee, BigDecimal amountOutUsd, BigDecimal totalCostUsd) {
        return new SwapQuoteService.QuotedSwap(q, token, null, null, networkFee, amountOutUsd, totalCostUsd);
    }

    @Test
    void toQuoteResponse_mapsRouteSymbolsMinAmountFiatAndIntentDeadline() {
        Instant deadline = Instant.now().plusSeconds(600);
        SwapIntentService.IntentToken token = new SwapIntentService.IntentToken("jti-1", "tok", deadline);
        SwapQuote q = quote(Map.of("minAmount", "5", "maxAmount", "9999"));

        SwapQuoteResponse resp = mapper.toQuoteResponse(
                quoted(q, token, null, new BigDecimal("96.4"), new BigDecimal("1.35")));

        assertThat(resp.quoteId()).isEqualTo("jti-1");
        assertThat(resp.intentToken()).isEqualTo("tok");
        assertThat(resp.route().provider()).isEqualTo("simpleswap");
        assertThat(resp.route().fromNetwork()).isEqualTo("USDT_TRC20");
        assertThat(resp.route().toNetwork()).isEqualTo("SOL");
        assertThat(resp.fromSymbol()).isEqualTo("USDT");
        assertThat(resp.toSymbol()).isEqualTo("SOL");
        assertThat(resp.minAmount()).isEqualByComparingTo("5");
        assertThat(resp.amountOutUsd()).isEqualByComparingTo("96.4");
        assertThat(resp.totalCostUsd()).isEqualByComparingTo("1.35");
        assertThat(resp.validUntil()).isEqualTo(deadline);
    }

    @Test
    void toQuoteResponse_noMinAmountMeta_defaultsToZero() {
        SwapIntentService.IntentToken token = new SwapIntentService.IntentToken("j", "t", Instant.now());
        SwapQuoteResponse resp = mapper.toQuoteResponse(
                quoted(quote(Map.of()), token, null, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(resp.minAmount()).isEqualByComparingTo("0");
    }

    @Test
    void toQuoteResponse_passesThroughNetworkFeeAndProviderBreakdown() {
        SwapIntentService.IntentToken token = new SwapIntentService.IntentToken("j", "t", Instant.now());
        NetworkFee networkFee = new NetworkFee(new BigDecimal("3"), "USDT", new BigDecimal("3.05"));

        SwapFeeBreakdownDto fees = mapper.toQuoteResponse(
                quoted(quote(Map.of()), token, networkFee, BigDecimal.ZERO, new BigDecimal("3.05"))).fees();

        assertThat(fees.providerFeePercent()).isEqualByComparingTo("0.01");
        assertThat(fees.providerFeeAmount()).isEqualByComparingTo("0.98");
        assertThat(fees.networkFee()).isSameAs(networkFee);
    }

    @Test
    void toQuoteResponse_nullNetworkFee_passedThrough() {
        SwapIntentService.IntentToken token = new SwapIntentService.IntentToken("j", "t", Instant.now());
        SwapQuoteResponse resp = mapper.toQuoteResponse(
                quoted(quote(Map.of()), token, null, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(resp.fees().networkFee()).isNull();
    }

    @Test
    void toExecuteResponse_mapsOrderFields() {
        SwapOrderEntity o = new SwapOrderEntity();
        o.setId(42L);
        o.setStatus(SwapOrderStatus.SOURCE_CONFIRMED);
        o.setProvider("simpleswap");
        o.setFromNetwork(BlockchainNetwork.BTC);
        o.setToNetwork(BlockchainNetwork.USDT_ERC20);
        o.setAmountIn(new BigDecimal("0.1"));
        o.setAmountOutExpected(new BigDecimal("6000"));
        o.setSourceTxHash("0xsrc");
        o.setDepositAddress("dep");

        SwapExecuteResponse resp = mapper.toExecuteResponse(o);

        assertThat(resp.orderId()).isEqualTo(42L);
        assertThat(resp.status()).isEqualTo(SwapOrderStatus.SOURCE_CONFIRMED);
        assertThat(resp.fromSymbol()).isEqualTo("BTC");
        assertThat(resp.toSymbol()).isEqualTo("USDT");
        assertThat(resp.sourceTxHash()).isEqualTo("0xsrc");
        assertThat(resp.depositAddress()).isEqualTo("dep");
    }

    @Test
    void toStatusResponse_mapsNetworkNamesAndSymbols() {
        SwapOrderEntity o = new SwapOrderEntity();
        o.setId(9L);
        o.setStatus(SwapOrderStatus.COMPLETED);
        o.setStatusReason("ok");
        o.setProvider("simpleswap");
        o.setFromNetwork(BlockchainNetwork.USDT_TRC20);
        o.setToNetwork(BlockchainNetwork.ETH);
        o.setAmountIn(new BigDecimal("100"));
        o.setAmountOutExpected(new BigDecimal("0.03"));
        o.setAmountOutActual(new BigDecimal("0.0299"));
        o.setSourceTxHash("0xsrc");
        o.setDestTxHash("0xdst");

        SwapOrderStatusResponse resp = mapper.toStatusResponse(o);

        assertThat(resp.fromNetwork()).isEqualTo("USDT_TRC20");
        assertThat(resp.toNetwork()).isEqualTo("ETH");
        assertThat(resp.fromSymbol()).isEqualTo("USDT");
        assertThat(resp.toSymbol()).isEqualTo("ETH");
        assertThat(resp.amountOutActual()).isEqualByComparingTo("0.0299");
        assertThat(resp.destTxHash()).isEqualTo("0xdst");
    }

    @Test
    void toRangeResponse_mapsNetworksAndBounds() {
        SwapRangeResponse resp = mapper.toRangeResponse(
                BlockchainNetwork.USDT_ERC20, BlockchainNetwork.BTC,
                new SwapRanges(new BigDecimal("5"), new BigDecimal("9000")));

        assertThat(resp.from()).isEqualTo("USDT_ERC20");
        assertThat(resp.to()).isEqualTo("BTC");
        assertThat(resp.min()).isEqualByComparingTo("5");
        assertThat(resp.max()).isEqualByComparingTo("9000");
    }

    @Test
    void toRangeResponse_passesThroughNullBounds() {
        SwapRangeResponse resp = mapper.toRangeResponse(
                BlockchainNetwork.USDT_ERC20, BlockchainNetwork.BTC,
                new SwapRanges(null, null));

        assertThat(resp.min()).isNull();
        assertThat(resp.max()).isNull();
    }
}
