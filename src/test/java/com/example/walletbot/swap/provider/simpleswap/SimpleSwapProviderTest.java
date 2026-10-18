package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.example.walletbot.swap.domain.*;
import com.example.walletbot.swap.domain.exception.*;
import com.example.walletbot.swap.provider.simpleswap.dto.SimpleSwapExchangeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimpleSwapProviderTest {

    private static final BlockchainNetwork FROM = BlockchainNetwork.USDT_ERC20;
    private static final BlockchainNetwork TO = BlockchainNetwork.BTC;
    private static final SwapPair PAIR = SwapPair.of(FROM, TO);

    private SimpleSwapClient client;
    private SimpleSwapCache cache;
    private BlockchainServiceFactory blockchainFactory;
    private SimpleSwapProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(SimpleSwapClient.class);
        cache = mock(SimpleSwapCache.class);
        blockchainFactory = mock(BlockchainServiceFactory.class);
        SimpleSwapProperties props = new SimpleSwapProperties(
                "https://api", "k", true, 60, 1000, 1000, 500,
                new BigDecimal("0.01"), new BigDecimal("0.02"), 10, true);
        provider = new SimpleSwapProvider(client, cache, new SimpleSwapCurrencyMap(), props, blockchainFactory);
    }

    @Test
    void supports_mappedConfiguredPair_true() {
        assertThat(provider.supports(PAIR)).isTrue();
    }

    @Test
    void supports_unmappedNetwork_false() {
        assertThat(provider.supports(SwapPair.of(BlockchainNetwork.BNB, BlockchainNetwork.USDT_ERC20)))
                .isFalse();
    }

    @Test
    void supports_unconfiguredProps_false() {
        SimpleSwapProperties off = new SimpleSwapProperties(
                "https://api", "k", false, 60, 1000, 1000, 500,
                new BigDecimal("0.01"), new BigDecimal("0.02"), 10, true);
        SimpleSwapProvider disabled = new SimpleSwapProvider(
                client, cache, new SimpleSwapCurrencyMap(), off, blockchainFactory);
        assertThat(disabled.supports(PAIR)).isFalse();
    }

    @Test
    void priority_reflectsProps() {
        assertThat(provider.priority()).isEqualTo(500);
    }

    @Test
    void ranges_unsupportedPair_emptyWithoutTouchingCache() {
        Optional<SwapRanges> r = provider.ranges(SwapPair.of(BlockchainNetwork.BNB, BlockchainNetwork.USDT_ERC20));
        assertThat(r).isEmpty();
        verifyNoInteractions(cache);
    }

    @Test
    void ranges_supportedPair_delegatesToCache() {
        when(cache.ranges(any(), any())).thenReturn(Optional.of(new SwapRanges(BigDecimal.ONE, BigDecimal.TEN)));
        assertThat(provider.ranges(PAIR)).contains(new SwapRanges(BigDecimal.ONE, BigDecimal.TEN));
    }

    @Test
    void quote_unsupportedPair_throwsQuoteUnavailable() {
        assertThatThrownBy(() -> provider.quote(
                SwapPair.of(BlockchainNetwork.BNB, BlockchainNetwork.USDT_ERC20), BigDecimal.TEN))
                .isInstanceOf(SwapQuoteUnavailableException.class);
    }

    @Test
    void quote_belowMin_throwsBelowMin() {
        when(cache.ranges(any(), any())).thenReturn(Optional.of(new SwapRanges(new BigDecimal("20"), null)));
        assertThatThrownBy(() -> provider.quote(PAIR, new BigDecimal("10")))
                .isInstanceOf(SwapAmountBelowMinException.class);
    }

    @Test
    void quote_aboveMax_throwsAboveMax() {
        when(cache.ranges(any(), any())).thenReturn(Optional.of(new SwapRanges(null, new BigDecimal("5"))));
        assertThatThrownBy(() -> provider.quote(PAIR, new BigDecimal("10")))
                .isInstanceOf(SwapAmountAboveMaxException.class);
    }

    @Test
    void quote_estimateMissing_throwsQuoteUnavailable() {
        when(cache.ranges(any(), any())).thenReturn(Optional.empty());
        when(cache.estimate(any(), any(), any())).thenReturn(null);
        assertThatThrownBy(() -> provider.quote(PAIR, new BigDecimal("10")))
                .isInstanceOf(SwapQuoteUnavailableException.class);
    }

    @Test
    void quote_happy_computesRateSlippageFeeAndMeta() {
        when(cache.ranges(any(), any())).thenReturn(Optional.of(
                new SwapRanges(new BigDecimal("1"), new BigDecimal("1000"))));
        when(cache.estimate(any(), any(), eq(new BigDecimal("10")))).thenReturn(new BigDecimal("9.5"));

        SwapQuote q = provider.quote(PAIR, new BigDecimal("10"));

        assertThat(q.providerId()).isEqualTo("simpleswap");
        assertThat(q.amountIn()).isEqualByComparingTo("10");
        assertThat(q.amountOut()).isEqualByComparingTo("9.5");
        assertThat(q.rate()).isEqualByComparingTo("0.95");
        assertThat(q.amountOutMin()).isEqualByComparingTo("9.31");
        assertThat(q.fees().providerFeePercent()).isEqualByComparingTo("0.01");
        assertThat(q.fees().providerFeeAmount()).isEqualByComparingTo("0.095");
        assertThat(q.providerMeta()).containsEntry("minAmount", "1").containsEntry("maxAmount", "1000");
    }

    @Test
    void prepare_carriesDepositAddressMemoAndTargetMeta() {
        SwapQuote q = new SwapQuote("simpleswap", null, PAIR, new BigDecimal("10"),
                new BigDecimal("9.5"), new BigDecimal("9.31"), new BigDecimal("0.95"),
                SwapFeeBreakdown.zero(), java.time.Instant.now().plusSeconds(60), Map.of());
        when(client.createExchange(any(), any(), eq(new BigDecimal("10")), eq("destAddr"), eq("refundAddr")))
                .thenReturn(new SimpleSwapExchangeDto("ex-99", "waiting",
                        "10", "9.5", "depositAddr", "memo-7", null, null));

        PreparedSwap prepared = provider.prepare(q, "refundAddr", "destAddr");

        assertThat(prepared.providerOrderId()).isEqualTo("ex-99");
        assertThat(prepared.depositAddress()).isEqualTo("depositAddr");
        assertThat(prepared.memo()).isEqualTo("memo-7");
        assertThat(prepared.meta()).containsEntry("addressTo", "destAddr");
    }

    private PreparedSwap prepared(String depositAddr, String memo) {
        return new PreparedSwap("simpleswap", "ex-1", PAIR, new BigDecimal("10"),
                depositAddr, memo, Map.of("addressTo", "destAddr"));
    }

    @Test
    void submit_plainChain_usesSendAndConfirms() {
        BlockchainService chain = mock(BlockchainService.class);
        when(blockchainFactory.getService(FROM)).thenReturn(chain);
        when(chain.send("pk", "depositAddr", new BigDecimal("10"))).thenReturn("0xhash");

        SubmittedSwap submitted = provider.submit(prepared("depositAddr", null),
                new WalletKeySource("fromAddr", "pk"));

        assertThat(submitted.sourceTxHash()).isEqualTo("0xhash");
        verify(chain).send("pk", "depositAddr", new BigDecimal("10"));
        verify(chain, never()).sendWithMemo(any(), any(), any(), any());
        verify(client).confirmExchange("ex-1", "0xhash", "depositAddr", "destAddr");
    }

    @Test
    void submit_memoChain_usesSendWithMemo() {
        BlockchainService chain = mock(BlockchainService.class);
        when(blockchainFactory.getService(FROM)).thenReturn(chain);
        when(chain.sendWithMemo("pk", "depositAddr", new BigDecimal("10"), "memo-1")).thenReturn("0xmemohash");

        SubmittedSwap submitted = provider.submit(prepared("depositAddr", "memo-1"),
                new WalletKeySource("fromAddr", "pk"));

        assertThat(submitted.sourceTxHash()).isEqualTo("0xmemohash");
        verify(chain).sendWithMemo("pk", "depositAddr", new BigDecimal("10"), "memo-1");
        verify(chain, never()).send(any(), any(), any());
    }

    @Test
    void submit_memoUnsupportedByChain_throwsProviderUnavailable_neverFallsBackToPlainSend() {
        BlockchainService chain = mock(BlockchainService.class);
        when(blockchainFactory.getService(FROM)).thenReturn(chain);
        when(chain.sendWithMemo(any(), any(), any(), any()))
                .thenThrow(new UnsupportedOperationException("no memo"));

        assertThatThrownBy(() -> provider.submit(prepared("depositAddr", "memo-1"),
                new WalletKeySource("fromAddr", "pk")))
                .isInstanceOf(SwapProviderUnavailableException.class);

        verify(chain, never()).send(any(), any(), any());
        verifyNoInteractions(client);
    }

    @Test
    void submit_insufficientFunds_mappedToInsufficientGas() {
        BlockchainService chain = mock(BlockchainService.class);
        when(blockchainFactory.getService(FROM)).thenReturn(chain);
        when(chain.send(any(), any(), any())).thenThrow(new InsufficientFundsException("broke"));

        assertThatThrownBy(() -> provider.submit(prepared("depositAddr", null),
                new WalletKeySource("fromAddr", "pk")))
                .isInstanceOf(SwapInsufficientGasException.class);
    }

    @Test
    void submit_rpcFailure_mappedToProviderUnavailable() {
        BlockchainService chain = mock(BlockchainService.class);
        when(blockchainFactory.getService(FROM)).thenReturn(chain);
        when(chain.send(any(), any(), any())).thenThrow(new BlockchainRpcException("node down"));

        assertThatThrownBy(() -> provider.submit(prepared("depositAddr", null),
                new WalletKeySource("fromAddr", "pk")))
                .isInstanceOf(SwapProviderUnavailableException.class);
    }

    @Test
    void submit_emptyTxHash_throwsProviderUnavailable() {
        BlockchainService chain = mock(BlockchainService.class);
        when(blockchainFactory.getService(FROM)).thenReturn(chain);
        when(chain.send(any(), any(), any())).thenReturn("  ");

        assertThatThrownBy(() -> provider.submit(prepared("depositAddr", null),
                new WalletKeySource("fromAddr", "pk")))
                .isInstanceOf(SwapProviderUnavailableException.class);
        verify(client, never()).confirmExchange(anyString(), anyString(), anyString(), anyString());
    }
}
