package com.example.walletbot.swap.application;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.application.intent.SwapIntentService;
import com.example.walletbot.swap.domain.SwapFeeBreakdown;
import com.example.walletbot.swap.domain.SwapPair;
import com.example.walletbot.swap.domain.SwapQuote;
import com.example.walletbot.swap.domain.SwapRanges;
import com.example.walletbot.swap.domain.exception.SwapAmountAboveMaxException;
import com.example.walletbot.swap.domain.exception.SwapAmountBelowMinException;
import com.example.walletbot.swap.routing.SwapRouter;
import com.example.walletbot.user.application.UserStatusGuard;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SwapQuoteServiceTest {

    private static final BlockchainNetwork FROM = BlockchainNetwork.USDT_TRC20;
    private static final BlockchainNetwork TO = BlockchainNetwork.SOL;

    @Mock
    SwapRouter router;
    @Mock
    WalletService walletService;
    @Mock
    PriceService priceService;
    @Mock
    SwapIntentService intentService;
    @Mock
    BlockchainServiceFactory blockchainFactory;
    @Mock
    BlockchainService tokenService;
    @Mock
    UserStatusGuard userStatusGuard;

    private SwapQuoteService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new SwapQuoteService(router, walletService, priceService,
                intentService, blockchainFactory, userStatusGuard);

        user = new UserEntity();
        user.setId(1L);

        when(walletService.getWalletForNetwork(user, FROM)).thenReturn(wallet(10L, "Tuser"));
        when(walletService.getWalletForNetwork(user, TO)).thenReturn(wallet(20L, "Suser"));

        when(blockchainFactory.getService(FROM)).thenReturn(tokenService);
        when(tokenService.estimateNativeBurn(eq("Tuser"), isNull(), any())).thenReturn(new BigDecimal("6.5"));

        when(priceService.usdValue(anyString(), any())).thenAnswer(inv -> {
            BigDecimal amt = inv.getArgument(1);
            if (amt == null) return BigDecimal.ZERO;
            BigDecimal price = switch ((String) inv.getArgument(0)) {
                case "BTC" -> new BigDecimal("65000");
                case "ETH" -> new BigDecimal("3000");
                case "SOL" -> new BigDecimal("150");
                case "USDT" -> BigDecimal.ONE;
                case "TRX" -> new BigDecimal("0.32");
                default -> BigDecimal.ZERO;
            };
            return amt.multiply(price);
        });

        when(router.selectBestQuote(any(), any())).thenAnswer(inv -> sampleQuote(inv.getArgument(1)));
        when(intentService.issue(anyLong(), any(), any(), any()))
                .thenReturn(new SwapIntentService.IntentToken("jti", "token", Instant.now().plusSeconds(60)));
    }

    @Test
    void quote_tokenSource_networkFeeIsUsersOwnNativeGasBurn() {
        SwapQuoteService.QuotedSwap result = service.quote(user, FROM, TO, new BigDecimal("100"));

        assertThat(result.networkFee()).isNotNull();
        assertThat(result.networkFee().symbol()).isEqualTo("TRX");
        assertThat(result.networkFee().amount()).isEqualByComparingTo("6.5");
        assertThat(result.networkFee().usdValue()).isEqualByComparingTo("2.08");
    }

    @Test
    void quote_amountBelowCachedMin_rejectedWithoutEstimating() {
        when(router.rangesFor(any())).thenReturn(Optional.of(
                new SwapRanges(new BigDecimal("10"), new BigDecimal("1000"))));

        assertThatThrownBy(() -> service.quote(user, FROM, TO, new BigDecimal("5")))
                .isInstanceOf(SwapAmountBelowMinException.class);

        verify(router, never()).selectBestQuote(any(), any());
    }

    @Test
    void quote_amountAboveCachedMax_rejectedWithoutEstimating() {
        when(router.rangesFor(any())).thenReturn(Optional.of(
                new SwapRanges(new BigDecimal("10"), new BigDecimal("1000"))));

        assertThatThrownBy(() -> service.quote(user, FROM, TO, new BigDecimal("5000")))
                .isInstanceOf(SwapAmountAboveMaxException.class);

        verify(router, never()).selectBestQuote(any(), any());
    }

    @Test
    void quote_nativeSource_networkFeeIsMinerFee_andAllInCostCapturesSpread() {
        BlockchainNetwork from = BlockchainNetwork.BTC;
        BlockchainNetwork to = BlockchainNetwork.USDT_ERC20;
        when(walletService.getWalletForNetwork(user, from)).thenReturn(wallet(40L, "btcaddr"));
        when(walletService.getWalletForNetwork(user, to)).thenReturn(wallet(41L, "ethaddr"));
        when(router.selectBestQuote(any(), any()))
                .thenAnswer(inv -> nativeQuote(from, to, inv.getArgument(1)));
        BlockchainService btcService = mock(BlockchainService.class);
        when(blockchainFactory.getService(from)).thenReturn(btcService);
        when(btcService.estimateSendFee()).thenReturn(new BigDecimal("0.0001"));

        SwapQuoteService.QuotedSwap result = service.quote(user, from, to, new BigDecimal("0.01"));

        assertThat(result.networkFee()).isNotNull();
        assertThat(result.networkFee().symbol()).isEqualTo("BTC");
        assertThat(result.networkFee().amount()).isEqualByComparingTo("0.0001");
        assertThat(result.networkFee().usdValue()).isEqualByComparingTo("6.5");
        assertThat(result.amountOutUsd()).isEqualByComparingTo("640");
        assertThat(result.totalCostUsd()).isEqualByComparingTo("16.5");
    }

    @Test
    void sourceFee_nativeSource_returnsMinerFeeAndReserveWithRetainedMinimum() {
        BlockchainService btc = mock(BlockchainService.class);
        when(blockchainFactory.getService(BlockchainNetwork.BTC)).thenReturn(btc);
        when(btc.estimateSendFee()).thenReturn(new BigDecimal("0.0001"));
        when(btc.minRetainedBalance()).thenReturn(new BigDecimal("0.0009"));

        SwapQuoteService.SourceFee result = service.sourceFee(BlockchainNetwork.BTC);

        assertThat(result.networkFee()).isNotNull();
        assertThat(result.networkFee().symbol()).isEqualTo("BTC");
        assertThat(result.networkFee().amount()).isEqualByComparingTo("0.0001");
        assertThat(result.networkFee().usdValue()).isEqualByComparingTo("6.5");
        assertThat(result.reserve()).isEqualByComparingTo("0.001");
    }

    @Test
    void sourceFee_tokenSource_returnsNulls() {
        SwapQuoteService.SourceFee result = service.sourceFee(BlockchainNetwork.USDT_TRC20);
        assertThat(result.networkFee()).isNull();
        assertThat(result.reserve()).isNull();
    }

    private WalletEntity wallet(long id, String address) {
        WalletEntity w = new WalletEntity();
        w.setId(id);
        w.setAddress(address);
        return w;
    }

    private SwapQuote sampleQuote(BigDecimal amountIn) {
        return new SwapQuote("simpleswap", null, SwapPair.of(FROM, TO),
                amountIn, amountIn, amountIn, BigDecimal.ONE,
                SwapFeeBreakdown.zero(),
                Instant.now().plusSeconds(60), Map.of());
    }

    private SwapQuote nativeQuote(BlockchainNetwork from, BlockchainNetwork to, BigDecimal amountIn) {
        return new SwapQuote("simpleswap", null, SwapPair.of(from, to),
                amountIn, new BigDecimal("640"), new BigDecimal("630"), new BigDecimal("64000"),
                SwapFeeBreakdown.zero(),
                Instant.now().plusSeconds(60), Map.of());
    }
}
