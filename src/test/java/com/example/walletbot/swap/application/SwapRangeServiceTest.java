package com.example.walletbot.swap.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.domain.SwapRanges;
import com.example.walletbot.swap.routing.SwapRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwapRangeServiceTest {

    @Mock
    SwapRouter router;

    private SwapRangeService service;

    @BeforeEach
    void setUp() {
        service = new SwapRangeService(router);
    }

    @Test
    void range_returnsProviderMinMax_forAllowedSupportedPair() {
        when(router.supports(any())).thenReturn(true);
        when(router.rangesFor(any())).thenReturn(Optional.of(
                new SwapRanges(new BigDecimal("5"), new BigDecimal("9000"))));

        SwapRanges range = service.range(BlockchainNetwork.USDT_ERC20, BlockchainNetwork.BTC);

        assertThat(range.min()).isEqualByComparingTo("5");
        assertThat(range.max()).isEqualByComparingTo("9000");
    }

    @Test
    void range_disallowedPair_returnsEmptyWithoutHittingRouter() {
        SwapRanges range = service.range(BlockchainNetwork.BTC, BlockchainNetwork.ETH);

        assertThat(range.min()).isNull();
        assertThat(range.max()).isNull();
        verify(router, never()).rangesFor(any());
    }

    @Test
    void range_unsupportedPair_returnsEmpty() {
        when(router.supports(any())).thenReturn(false);

        SwapRanges range = service.range(BlockchainNetwork.USDT_ERC20, BlockchainNetwork.BNB);

        assertThat(range.min()).isNull();
        assertThat(range.max()).isNull();
        verify(router, never()).rangesFor(any());
    }

    @Test
    void range_providerThrows_returnsEmptyInsteadOfPropagating() {
        when(router.supports(any())).thenReturn(true);
        when(router.rangesFor(any())).thenThrow(new RuntimeException("provider down"));

        SwapRanges range = service.range(BlockchainNetwork.USDT_ERC20, BlockchainNetwork.BTC);

        assertThat(range.min()).isNull();
        assertThat(range.max()).isNull();
    }
}
