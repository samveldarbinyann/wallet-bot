package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.swap.domain.SubmittedSwap;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.swap.domain.SwapStatusUpdate;
import com.example.walletbot.swap.provider.simpleswap.dto.SimpleSwapExchangeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimpleSwapProviderTrackTest {

    private SimpleSwapClient client;
    private SimpleSwapProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(SimpleSwapClient.class);
        SimpleSwapProperties props = new SimpleSwapProperties(
                "https://api", "k", true, 60, 1000, 1000, 500,
                new BigDecimal("0.01"), new BigDecimal("0.02"), 10, true);
        provider = new SimpleSwapProvider(client, mock(SimpleSwapCache.class),
                new SimpleSwapCurrencyMap(), props, mock(BlockchainServiceFactory.class));
    }

    private SimpleSwapExchangeDto dtoWithStatus(String status) {
        return new SimpleSwapExchangeDto("ex1", status,
                "0.05", "0.8", "bcAddr", null, "txFrom", "txTo");
    }

    @Test
    void track_waiting_returnsNoChange() {
        when(client.getExchange(anyString())).thenReturn(Optional.of(dtoWithStatus("waiting")));
        SwapStatusUpdate u = provider.track(new SubmittedSwap("simpleswap", "ex1", "tx"));
        assertThat(u.hasChange()).isFalse();
    }

    @Test
    void track_confirming_returnsSourceConfirmed() {
        when(client.getExchange(anyString())).thenReturn(Optional.of(dtoWithStatus("confirming")));
        SwapStatusUpdate u = provider.track(new SubmittedSwap("simpleswap", "ex1", "tx"));
        assertThat(u.status()).isEqualTo(SwapOrderStatus.SOURCE_CONFIRMED);
    }

    @Test
    void track_exchanging_returnsDestAwaiting() {
        when(client.getExchange(anyString())).thenReturn(Optional.of(dtoWithStatus("exchanging")));
        SwapStatusUpdate u = provider.track(new SubmittedSwap("simpleswap", "ex1", "tx"));
        assertThat(u.status()).isEqualTo(SwapOrderStatus.DEST_AWAITING);
    }

    @Test
    void track_finished_returnsCompletedWithAmountAndTx() {
        when(client.getExchange(anyString())).thenReturn(Optional.of(dtoWithStatus("finished")));
        SwapStatusUpdate u = provider.track(new SubmittedSwap("simpleswap", "ex1", "tx"));
        assertThat(u.status()).isEqualTo(SwapOrderStatus.COMPLETED);
        assertThat(u.destTxHash()).isEqualTo("txTo");
        assertThat(u.amountOutActual()).isEqualByComparingTo("0.8");
    }

    @Test
    void track_failed_returnsFailedProvider() {
        when(client.getExchange(anyString())).thenReturn(Optional.of(dtoWithStatus("failed")));
        SwapStatusUpdate u = provider.track(new SubmittedSwap("simpleswap", "ex1", "tx"));
        assertThat(u.status()).isEqualTo(SwapOrderStatus.FAILED_PROVIDER);
    }

    @Test
    void track_refunded_returnsRefunded() {
        when(client.getExchange(anyString())).thenReturn(Optional.of(dtoWithStatus("refunded")));
        SwapStatusUpdate u = provider.track(new SubmittedSwap("simpleswap", "ex1", "tx"));
        assertThat(u.status()).isEqualTo(SwapOrderStatus.REFUNDED);
    }

    @Test
    void track_expired_returnsExpired() {
        when(client.getExchange(anyString())).thenReturn(Optional.of(dtoWithStatus("expired")));
        SwapStatusUpdate u = provider.track(new SubmittedSwap("simpleswap", "ex1", "tx"));
        assertThat(u.status()).isEqualTo(SwapOrderStatus.EXPIRED);
    }

    @Test
    void track_unknownStatus_returnsNoChange() {
        when(client.getExchange(anyString())).thenReturn(Optional.of(dtoWithStatus("alien-state")));
        SwapStatusUpdate u = provider.track(new SubmittedSwap("simpleswap", "ex1", "tx"));
        assertThat(u.hasChange()).isFalse();
    }

    @Test
    void track_emptyResponse_returnsNoChange() {
        when(client.getExchange(anyString())).thenReturn(Optional.empty());
        SwapStatusUpdate u = provider.track(new SubmittedSwap("simpleswap", "ex1", "tx"));
        assertThat(u.hasChange()).isFalse();
    }
}
