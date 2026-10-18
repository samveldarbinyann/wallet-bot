package com.example.walletbot.swap.application;

import com.example.walletbot.asset.application.AssetService;
import com.example.walletbot.asset.domain.OutgoingReservationPort;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.platform.persistence.WalletLockService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.shared.exception.AccountFrozenException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.swap.application.intent.SwapIntent;
import com.example.walletbot.swap.application.intent.SwapIntentService;
import com.example.walletbot.swap.application.policy.SwapRateLimiter;
import com.example.walletbot.swap.domain.*;
import com.example.walletbot.swap.domain.exception.SwapInsufficientGasException;
import com.example.walletbot.swap.domain.exception.SwapIntentInvalidException;
import com.example.walletbot.swap.domain.exception.SwapProviderUnavailableException;
import com.example.walletbot.swap.domain.exception.SwapQuoteUnavailableException;
import com.example.walletbot.swap.provider.SwapProvider;
import com.example.walletbot.swap.routing.ProviderRegistry;
import com.example.walletbot.user.application.UserStatusGuard;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SwapExecutionServiceTest {

    private static final String TOKEN = "token";
    private static final String IDEM = "idem-1";
    @Mock
    SwapIntentService intentService;
    @Mock
    SwapOrderRepository orderRepository;
    @Mock
    SwapOrderMutator orderMutator;
    @Mock
    ProviderRegistry providerRegistry;
    @Mock
    WalletService walletService;
    @Mock
    AssetService assetService;
    @Mock
    OutgoingReservationPort reservationPort;
    @Mock
    SwapRateLimiter rateLimiter;
    @Mock
    BlockchainServiceFactory blockchainFactory;
    @Mock
    UserStatusGuard userStatusGuard;
    @Mock
    WalletLockService walletLockService;
    @Mock
    SwapProvider provider;
    @Mock
    BlockchainService tokenChainService;
    @Mock
    BlockchainService nativeChainService;
    private SwapExecutionService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new SwapExecutionService(
                intentService, orderRepository, orderMutator, providerRegistry,
                walletService, assetService, reservationPort, rateLimiter, walletLockService,
                blockchainFactory, userStatusGuard);
        user = new UserEntity();
        user.setId(7L);

        when(walletLockService.withLock(anyLong(), any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> body = inv.getArgument(1);
            return WalletLockService.LockResult.acquired(body.get());
        });

        doAnswer(inv -> {
            SwapOrderEntity o = inv.getArgument(0);
            Consumer<SwapOrderEntity> c = inv.getArgument(1);
            c.accept(o);
            return null;
        }).when(orderMutator).applyUpdate(any(SwapOrderEntity.class), any());
    }

    private SwapIntent intent(BlockchainNetwork from, BlockchainNetwork to) {
        return new SwapIntent("jti", 7L, from, to,
                new BigDecimal("0.1"), new BigDecimal("1.6"), new BigDecimal("1.55"),
                "simpleswap", "qid", "bcAddr", "0xAddr",
                Instant.now().plusSeconds(60));
    }

    private WalletEntity wallet(long id, String address) {
        WalletEntity w = new WalletEntity();
        w.setId(id);
        w.setAddress(address);
        w.setPrivateKey("pk-" + id);
        return w;
    }

    private SwapOrderEntity authorizedOrder(BlockchainNetwork from, BlockchainNetwork to) {
        SwapOrderEntity o = new SwapOrderEntity();
        o.setId(100L);
        o.setUserId(7L);
        o.setProvider("simpleswap");
        o.setProviderQuoteId("qid");
        o.setFromNetwork(from);
        o.setToNetwork(to);
        o.setFromAddress("bcAddr");
        o.setToAddress("0xAddr");
        o.setAmountIn(new BigDecimal("0.1"));
        o.setAmountOutExpected(new BigDecimal("1.6"));
        o.setAmountOutMin(new BigDecimal("1.55"));
        o.setRateQuoted(new BigDecimal("16"));
        o.setExpiresAt(Instant.now().plusSeconds(600));
        o.setStatus(SwapOrderStatus.AUTHORIZED);
        return o;
    }

    private SwapOrderEntity primeNativeSource() {
        SwapIntent intent = intent(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        when(intentService.verify(TOKEN, 7L)).thenReturn(intent);
        when(orderRepository.findByUserIdAndIdemKey(7L, IDEM)).thenReturn(Optional.empty());
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.BTC)).thenReturn(wallet(10L, "bcAddr"));
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet(20L, "0xAddr"));
        when(rateLimiter.isAllowed(7L, 10L)).thenReturn(true);
        when(assetService.getBalance(7L, 10L, "bcAddr", BlockchainNetwork.BTC)).thenReturn("100");
        when(blockchainFactory.getService(BlockchainNetwork.BTC)).thenReturn(nativeChainService);
        when(nativeChainService.estimateSendFee()).thenReturn(new BigDecimal("0.0001"));

        SwapOrderEntity order = authorizedOrder(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        when(orderMutator.createAuthorizedOrder(eq(user), any(), any(), any(), eq(IDEM))).thenReturn(order);

        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.prepare(any(), eq("bcAddr"), eq("0xAddr")))
                .thenReturn(new PreparedSwap("simpleswap", "ex1",
                        SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.ETH),
                        new BigDecimal("0.1"), "depositAddr", null, Map.of()));
        when(provider.submit(any(), any()))
                .thenReturn(new SubmittedSwap("simpleswap", "ex1", "0xsourcetx"));
        return order;
    }

    @Test
    void execute_nativeSource_broadcastsToSourceConfirmed_andRecordsRateLimit() {
        SwapOrderEntity order = primeNativeSource();

        SwapOrderEntity result = service.execute(user, TOKEN, IDEM);

        assertThat(result.getStatus()).isEqualTo(SwapOrderStatus.SOURCE_CONFIRMED);
        assertThat(result.getSourceTxHash()).isEqualTo("0xsourcetx");
        assertThat(result.getProviderOrderId()).isEqualTo("ex1");
        assertThat(result.getDepositAddress()).isEqualTo("depositAddr");
        verify(provider).prepare(any(), eq("bcAddr"), eq("0xAddr"));
        verify(provider).submit(any(), any());
        verify(rateLimiter).record(7L, 10L);
    }

    @Test
    void execute_submitMemoless_passesWalletKeysThrough() {
        primeNativeSource();

        service.execute(user, TOKEN, IDEM);

        var keyCaptor = ArgumentCaptor.forClass(WalletKeySource.class);
        verify(provider).submit(any(), keyCaptor.capture());
        assertThat(keyCaptor.getValue().fromAddress()).isEqualTo("bcAddr");
        assertThat(keyCaptor.getValue().privateKey()).isEqualTo("pk-10");
    }

    @Test
    void execute_frozenUser_blockedBeforeAnyWork() {
        doThrow(new AccountFrozenException("frozen")).when(userStatusGuard).requireCanTransact(user);
        user.setStatus(UserStatus.FROZEN);

        assertThatThrownBy(() -> service.execute(user, TOKEN, IDEM))
                .isInstanceOf(AccountFrozenException.class);

        verify(intentService, never()).verify(anyString(), anyLong());
    }

    @Test
    void execute_destinationWalletMismatch_throwsIntentInvalid() {
        when(intentService.verify(TOKEN, 7L))
                .thenReturn(intent(BlockchainNetwork.BTC, BlockchainNetwork.ETH));
        when(orderRepository.findByUserIdAndIdemKey(7L, IDEM)).thenReturn(Optional.empty());
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.BTC)).thenReturn(wallet(10L, "bcAddr"));
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH))
                .thenReturn(wallet(20L, "wrongDestAddr"));

        assertThatThrownBy(() -> service.execute(user, TOKEN, IDEM))
                .isInstanceOf(SwapIntentInvalidException.class);
        verify(orderMutator, never()).createAuthorizedOrder(any(), any(), any(), any(), any());
    }

    @Test
    void execute_rateLimited_throwsAndNeverBroadcasts() {
        when(intentService.verify(TOKEN, 7L))
                .thenReturn(intent(BlockchainNetwork.BTC, BlockchainNetwork.ETH));
        when(orderRepository.findByUserIdAndIdemKey(7L, IDEM)).thenReturn(Optional.empty());
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.BTC)).thenReturn(wallet(10L, "bcAddr"));
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet(20L, "0xAddr"));
        when(rateLimiter.isAllowed(7L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.execute(user, TOKEN, IDEM))
                .isInstanceOf(RateLimitException.class);
        verifyNoInteractions(providerRegistry);
    }

    @Test
    void execute_insufficientBalance_throwsBeforeAuthorizing() {
        when(intentService.verify(TOKEN, 7L))
                .thenReturn(intent(BlockchainNetwork.BTC, BlockchainNetwork.ETH));
        when(orderRepository.findByUserIdAndIdemKey(7L, IDEM)).thenReturn(Optional.empty());
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.BTC)).thenReturn(wallet(10L, "bcAddr"));
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet(20L, "0xAddr"));
        when(rateLimiter.isAllowed(7L, 10L)).thenReturn(true);
        when(assetService.getBalance(7L, 10L, "bcAddr", BlockchainNetwork.BTC)).thenReturn("0.05");

        assertThatThrownBy(() -> service.execute(user, TOKEN, IDEM))
                .isInstanceOf(InsufficientFundsException.class);
        verify(orderMutator, never()).createAuthorizedOrder(any(), any(), any(), any(), any());
    }

    @Test
    void execute_nativeSource_wholeBalanceLeavesNoRoomForFee_throwsInsufficientGas() {
        when(intentService.verify(TOKEN, 7L))
                .thenReturn(intent(BlockchainNetwork.BTC, BlockchainNetwork.ETH));
        when(orderRepository.findByUserIdAndIdemKey(7L, IDEM)).thenReturn(Optional.empty());
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.BTC)).thenReturn(wallet(10L, "bcAddr"));
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet(20L, "0xAddr"));
        when(rateLimiter.isAllowed(7L, 10L)).thenReturn(true);
        when(assetService.getBalance(7L, 10L, "bcAddr", BlockchainNetwork.BTC)).thenReturn("0.1");
        when(blockchainFactory.getService(BlockchainNetwork.BTC)).thenReturn(nativeChainService);
        when(nativeChainService.estimateSendFee()).thenReturn(new BigDecimal("0.0001"));

        assertThatThrownBy(() -> service.execute(user, TOKEN, IDEM))
                .isInstanceOf(SwapInsufficientGasException.class);

        verify(orderMutator, never()).createAuthorizedOrder(any(), any(), any(), any(), any());
        verifyNoInteractions(providerRegistry);
    }

    @Test
    void execute_providerNotRegistered_marksFailedProvider() {
        primeNativeSource();
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(user, TOKEN, IDEM))
                .isInstanceOf(SwapQuoteUnavailableException.class);

        verify(orderMutator).markFailed(any(SwapOrderEntity.class),
                eq(SwapOrderStatus.FAILED_PROVIDER), anyString());
        verify(rateLimiter, never()).record(anyLong(), anyLong());
    }

    @Test
    void execute_providerPrepareDown_marksFailedSource() {
        primeNativeSource();
        when(provider.prepare(any(), any(), any()))
                .thenThrow(new SwapProviderUnavailableException("provider down"));

        assertThatThrownBy(() -> service.execute(user, TOKEN, IDEM))
                .isInstanceOf(SwapProviderUnavailableException.class);

        verify(orderMutator).markFailed(any(SwapOrderEntity.class),
                eq(SwapOrderStatus.FAILED_SOURCE), anyString());
    }

    @Test
    void execute_unexpectedRuntimeError_wrappedAsProviderUnavailable_failedSource() {
        primeNativeSource();
        when(provider.submit(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.execute(user, TOKEN, IDEM))
                .isInstanceOf(SwapProviderUnavailableException.class);

        verify(orderMutator).markFailed(any(SwapOrderEntity.class),
                eq(SwapOrderStatus.FAILED_SOURCE), eq("Internal error"));
    }

    @Test
    void execute_tokenSource_walletHasGas_broadcasts() {
        primeTokenSource("50");

        SwapOrderEntity result = service.execute(user, TOKEN, IDEM);

        assertThat(result.getStatus()).isEqualTo(SwapOrderStatus.SOURCE_CONFIRMED);
        verify(provider).submit(any(), any());
        verify(rateLimiter).record(7L, 10L);
    }

    @Test
    void execute_tokenSource_noNativeGas_throwsInsufficientGasBeforeAuthorizing() {
        primeTokenSource("0");

        assertThatThrownBy(() -> service.execute(user, TOKEN, IDEM))
                .isInstanceOf(SwapInsufficientGasException.class);

        verify(orderMutator, never()).createAuthorizedOrder(any(), any(), any(), any(), any());
        verifyNoInteractions(providerRegistry);
    }

    private void primeTokenSource(String gasBalance) {
        SwapIntent intent = intent(BlockchainNetwork.USDT_TRC20, BlockchainNetwork.SOL);
        when(intentService.verify(TOKEN, 7L)).thenReturn(intent);
        when(orderRepository.findByUserIdAndIdemKey(7L, IDEM)).thenReturn(Optional.empty());
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.USDT_TRC20)).thenReturn(wallet(10L, "bcAddr"));
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.SOL)).thenReturn(wallet(20L, "0xAddr"));
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.TRX)).thenReturn(wallet(30L, "trxGasAddr"));
        when(rateLimiter.isAllowed(7L, 10L)).thenReturn(true);
        when(assetService.getBalance(7L, 10L, "bcAddr", BlockchainNetwork.USDT_TRC20)).thenReturn("100");
        when(assetService.getBalance(7L, 30L, "trxGasAddr", BlockchainNetwork.TRX)).thenReturn(gasBalance);

        when(blockchainFactory.getService(BlockchainNetwork.USDT_TRC20)).thenReturn(tokenChainService);
        when(tokenChainService.estimateNativeBurn(eq("bcAddr"), isNull(), any())).thenReturn(new BigDecimal("10"));

        SwapOrderEntity order = authorizedOrder(BlockchainNetwork.USDT_TRC20, BlockchainNetwork.SOL);
        when(orderMutator.createAuthorizedOrder(eq(user), any(), any(), any(), eq(IDEM))).thenReturn(order);

        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.prepare(any(), eq("bcAddr"), eq("0xAddr")))
                .thenReturn(new PreparedSwap("simpleswap", "ex1",
                        SwapPair.of(BlockchainNetwork.USDT_TRC20, BlockchainNetwork.SOL),
                        new BigDecimal("0.1"), "depositAddr", null, Map.of()));
        when(provider.submit(any(), any()))
                .thenReturn(new SubmittedSwap("simpleswap", "ex1", "0xsourcetx"));
    }
}
