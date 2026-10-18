package com.example.walletbot.swap.application;

import com.example.walletbot.asset.application.AssetService;
import com.example.walletbot.asset.domain.OutgoingReservationPort;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.platform.persistence.WalletLockService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.swap.application.intent.SwapIntent;
import com.example.walletbot.swap.application.intent.SwapIntentService;
import com.example.walletbot.swap.application.policy.SwapRateLimiter;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.swap.routing.ProviderRegistry;
import com.example.walletbot.user.application.UserStatusGuard;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwapExecutionServiceIdempotencyTest {

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

    private SwapExecutionService service;

    @BeforeEach
    void setUp() {
        service = new SwapExecutionService(
                intentService, orderRepository, orderMutator, providerRegistry,
                walletService, assetService, reservationPort, rateLimiter, walletLockService,
                blockchainFactory, userStatusGuard);
    }

    @Test
    void execute_missingIdemKey_throws() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        assertThatThrownBy(() -> service.execute(user, "any-token", null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.execute(user, "any-token", ""))
                .isInstanceOf(BadRequestException.class);
        verify(intentService, never()).verify(anyString(), anyLong());
    }

    @Test
    void execute_existingOrderForIdemKey_shortCircuits() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        SwapIntent intent = new SwapIntent(
                "jti", 7L, BlockchainNetwork.BTC, BlockchainNetwork.ETH,
                new BigDecimal("0.1"), new BigDecimal("1.6"), new BigDecimal("1.55"),
                "simpleswap", "qid", "bcAddr", "0xAddr",
                Instant.now().plusSeconds(60));
        when(intentService.verify("token", 7L)).thenReturn(intent);

        SwapOrderEntity existing = new SwapOrderEntity();
        existing.setId(101L);
        existing.setStatus(SwapOrderStatus.SOURCE_CONFIRMED);
        when(orderRepository.findByUserIdAndIdemKey(7L, "idem-1")).thenReturn(Optional.of(existing));

        SwapOrderEntity result = service.execute(user, "token", "idem-1");
        assertThat(result.getId()).isEqualTo(101L);

        verify(walletService, never()).getWalletForNetwork(any(), any());
        verify(rateLimiter, never()).isAllowed(anyLong(), anyLong());
        verify(providerRegistry, never()).byId(anyString());
    }

    @Test
    void execute_sourceWalletMismatch_throwsIntentInvalid() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        SwapIntent intent = new SwapIntent(
                "jti", 7L, BlockchainNetwork.BTC, BlockchainNetwork.ETH,
                new BigDecimal("0.1"), new BigDecimal("1.6"), new BigDecimal("1.55"),
                "simpleswap", "qid", "bcAddrInIntent", "0xAddr",
                Instant.now().plusSeconds(60));
        when(intentService.verify("token", 7L)).thenReturn(intent);
        when(orderRepository.findByUserIdAndIdemKey(7L, "idem-1")).thenReturn(Optional.empty());

        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setAddress("differentAddr");
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.BTC)).thenReturn(wallet);

        assertThatThrownBy(() -> service.execute(user, "token", "idem-1"))
                .isInstanceOf(com.example.walletbot.swap.domain.exception.SwapIntentInvalidException.class);
    }
}
