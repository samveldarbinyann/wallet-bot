package com.example.walletbot.transaction.application;

import com.example.walletbot.addressbook.application.AddressBookService;
import com.example.walletbot.asset.application.AssetService;
import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.platform.persistence.WalletLockService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.example.walletbot.shared.util.AddressNormalizer;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import com.example.walletbot.transaction.infrastructure.IdempotencyStore;
import com.example.walletbot.user.application.UserStatusGuard;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SendTransactionServiceTest {

    private static final BlockchainNetwork TOKEN = BlockchainNetwork.USDT_TRC20;
    private static final BlockchainNetwork NATIVE = BlockchainNetwork.TRX;
    private static final Long USER_ID = 1L;
    private static final Long WALLET_ID = 10L;
    private static final String ADDRESS = "Tuser";

    @Mock AssetService assetService;
    @Mock BlockchainServiceFactory blockchainFactory;
    @Mock PriceService priceService;
    @Mock TransactionLogService transactionLogService;
    @Mock SendRateLimiter sendRateLimiter;
    @Mock AddressBookService addressBookService;
    @Mock AddressNormalizer addressNormalizer;
    @Mock IdempotencyStore idempotencyStore;
    @Mock BlockchainService tokenBlockchainService;
    @Mock BlockchainService nativeBlockchainService;
    @Mock
    UserStatusGuard userStatusGuard;
    @Mock
    WalletLockService walletLockService;
    @Mock
    OutgoingReservationService reservationService;

    @InjectMocks SendTransactionService service;

    private UserEntity user;
    private WalletEntity wallet;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(USER_ID);
        user.setStatus(com.example.walletbot.shared.constant.UserStatus.ACTIVE);

        wallet = new WalletEntity();
        wallet.setId(WALLET_ID);
        wallet.setAddress(ADDRESS);
        wallet.setNetwork(TOKEN);
        wallet.setPrivateKey("privkey");

        when(blockchainFactory.getService(TOKEN)).thenReturn(tokenBlockchainService);
        when(blockchainFactory.getService(NATIVE)).thenReturn(nativeBlockchainService);
        when(tokenBlockchainService.estimateSendFee()).thenReturn(new BigDecimal("14"));
        when(tokenBlockchainService.estimateNativeBurn(any(), any(), any())).thenReturn(new BigDecimal("14"));
        when(tokenBlockchainService.getFeeSymbol()).thenReturn("TRX");
        when(priceService.usdValue(any(), any())).thenReturn(new BigDecimal("1.4"));

        when(reservationService.effectiveAvailable(anyLong(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(walletLockService.withLock(anyLong(), any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> body = inv.getArgument(1);
            return WalletLockService.LockResult.acquired(body.get());
        });
        TransactionLogEntity broadcastRow = new TransactionLogEntity();
        broadcastRow.setId(123L);
        when(transactionLogService.createPendingWithHash(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(broadcastRow);
    }

    @Test
    void buildEstimate_token_enoughNativeGas() {
        when(assetService.getBalance(USER_ID, WALLET_ID, ADDRESS, TOKEN)).thenReturn("100");
        when(assetService.getBalance(USER_ID, WALLET_ID, ADDRESS, NATIVE)).thenReturn("50");

        SendTransactionService.EstimateResult result =
                service.buildEstimate(user, wallet, TOKEN, new BigDecimal("90"), false);

        assertThat(result.hasEnoughGas()).isTrue();
        assertThat(result.gasShortfall().compareTo(BigDecimal.ZERO)).isZero();
        assertThat(result.networkFee().amount().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        assertThat(result.networkFee().symbol()).isEqualTo("TRX");
        assertThat(result.maxSendable().compareTo(new BigDecimal("100"))).isZero();
        assertThat(result.recipientAmount().compareTo(new BigDecimal("90"))).isZero();
    }

    @Test
    void buildEstimate_max_recipientAmountIsMaxSendable() {
        when(assetService.getBalance(USER_ID, WALLET_ID, ADDRESS, TOKEN)).thenReturn("100");
        when(assetService.getBalance(USER_ID, WALLET_ID, ADDRESS, NATIVE)).thenReturn("50");

        SendTransactionService.EstimateResult result =
                service.buildEstimate(user, wallet, TOKEN, new BigDecimal("90"), true);

        assertThat(result.recipientAmount().compareTo(result.maxSendable())).isZero();
    }

    @Test
    void buildEstimate_token_noNativeGas_shortfallReported() {
        when(assetService.getBalance(USER_ID, WALLET_ID, ADDRESS, TOKEN)).thenReturn("100");
        when(assetService.getBalance(USER_ID, WALLET_ID, ADDRESS, NATIVE)).thenReturn("0");

        SendTransactionService.EstimateResult result =
                service.buildEstimate(user, wallet, TOKEN, null, false);

        assertThat(result.hasEnoughGas()).isFalse();
        assertThat(result.gasShortfall().compareTo(new BigDecimal("14"))).isZero();
    }

    @Test
    void buildEstimate_native_deductsOneFee() {
        WalletEntity ethWallet = new WalletEntity();
        ethWallet.setId(2L);
        ethWallet.setAddress("0xUser");
        ethWallet.setNetwork(BlockchainNetwork.ETH);

        BlockchainService ethService = mock(BlockchainService.class);
        when(blockchainFactory.getService(BlockchainNetwork.ETH)).thenReturn(ethService);
        when(ethService.estimateNativeBurn(any(), any(), any())).thenReturn(new BigDecimal("0.001"));
        when(ethService.getFeeSymbol()).thenReturn("ETH");
        when(ethService.minRetainedBalance()).thenReturn(BigDecimal.ZERO);
        when(assetService.getBalance(USER_ID, 2L, "0xUser", BlockchainNetwork.ETH)).thenReturn("1.0");

        SendTransactionService.EstimateResult result =
                service.buildEstimate(user, ethWallet, BlockchainNetwork.ETH, new BigDecimal("0.5"), false);

        assertThat(result.hasEnoughGas()).isTrue();
        assertThat(result.maxSendable().compareTo(new BigDecimal("0.99900000"))).isZero();
    }

    @Test
    void buildEstimate_native_reservesRetainedMinimumOnTopOfFee() {
        WalletEntity solWallet = new WalletEntity();
        solWallet.setId(3L);
        solWallet.setAddress("SoLUser");
        solWallet.setNetwork(BlockchainNetwork.SOL);

        BlockchainService solService = mock(BlockchainService.class);
        when(blockchainFactory.getService(BlockchainNetwork.SOL)).thenReturn(solService);
        when(solService.estimateNativeBurn(any(), any(), any())).thenReturn(new BigDecimal("0.000005"));
        when(solService.getFeeSymbol()).thenReturn("SOL");
        when(solService.minRetainedBalance()).thenReturn(new BigDecimal("0.00089088"));
        when(assetService.getBalance(USER_ID, 3L, "SoLUser", BlockchainNetwork.SOL)).thenReturn("1.0");

        SendTransactionService.EstimateResult result =
                service.buildEstimate(user, solWallet, BlockchainNetwork.SOL, new BigDecimal("0.5"), false);

        assertThat(result.maxSendable().compareTo(new BigDecimal("0.99910412"))).isZero();
    }

    @Test
    void send_token_enoughNative_broadcasts() {
        when(sendRateLimiter.isAllowed(USER_ID, WALLET_ID)).thenReturn(true);
        when(tokenBlockchainService.deriveAddress("privkey")).thenReturn(ADDRESS);
        when(addressNormalizer.sameAddress(TOKEN, ADDRESS, ADDRESS)).thenReturn(true);
        when(assetService.getFreshBalance(ADDRESS, TOKEN)).thenReturn("100");
        when(assetService.getFreshBalance(ADDRESS, NATIVE)).thenReturn("50");
        when(tokenBlockchainService.send(any(), any(), any())).thenReturn("TX_HASH");

        SendTransactionService.SendResult result =
                service.send(user, wallet, TOKEN, "Tdest", new BigDecimal("10"), false, "USDT", 1L);

        assertThat(result.txHash()).isEqualTo("TX_HASH");
        assertThat(result.txId()).isEqualTo(123L);
        verify(idempotencyStore).complete(1L, "TX_HASH");
    }

    @Test
    void send_token_noNativeGas_rejectedBeforeBroadcast() {
        when(sendRateLimiter.isAllowed(USER_ID, WALLET_ID)).thenReturn(true);
        when(tokenBlockchainService.deriveAddress("privkey")).thenReturn(ADDRESS);
        when(addressNormalizer.sameAddress(TOKEN, ADDRESS, ADDRESS)).thenReturn(true);
        when(assetService.getFreshBalance(ADDRESS, TOKEN)).thenReturn("100");
        when(assetService.getFreshBalance(ADDRESS, NATIVE)).thenReturn("0");

        assertThatThrownBy(() -> service.send(user, wallet, TOKEN, "Tdest", new BigDecimal("10"), false, "USDT", 1L))
                .isInstanceOf(InsufficientFundsException.class);

        verify(transactionLogService, never()).createPendingWithHash(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(tokenBlockchainService, never()).send(any(), any(), any());
    }

    @Test
    void send_successfulBroadcast_createsPendingWithHash_doesNotMarkSuccess() {
        when(sendRateLimiter.isAllowed(USER_ID, WALLET_ID)).thenReturn(true);
        when(tokenBlockchainService.deriveAddress("privkey")).thenReturn(ADDRESS);
        when(addressNormalizer.sameAddress(TOKEN, ADDRESS, ADDRESS)).thenReturn(true);
        when(assetService.getFreshBalance(ADDRESS, TOKEN)).thenReturn("100");
        when(assetService.getFreshBalance(ADDRESS, NATIVE)).thenReturn("50");
        when(tokenBlockchainService.send(any(), any(), any())).thenReturn("TX_HASH");

        SendTransactionService.SendResult result =
                service.send(user, wallet, TOKEN, "Tdest", new BigDecimal("10"), false, "USDT", 1L);

        assertThat(result.txHash()).isEqualTo("TX_HASH");
        verify(transactionLogService).createPendingWithHash(
                eq(USER_ID), eq(WALLET_ID), eq(TransactionLogEntity.TxType.SEND),
                eq(TOKEN), eq("USDT"), eq(new BigDecimal("10")),
                eq(ADDRESS), eq("Tdest"), eq("TX_HASH"), any());
        verify(transactionLogService, never()).markSuccess(any(), any(), any(), any());
        verify(transactionLogService, never()).markFailed(any(), any());
    }

    @Test
    void send_nativeMax_recomputesAmountFromFreshBalanceAndFee_ignoringProbe() {
        WalletEntity ethWallet = new WalletEntity();
        ethWallet.setId(2L);
        ethWallet.setAddress("0xUser");
        ethWallet.setNetwork(BlockchainNetwork.ETH);
        ethWallet.setPrivateKey("pk");

        BlockchainService ethService = mock(BlockchainService.class);
        when(blockchainFactory.getService(BlockchainNetwork.ETH)).thenReturn(ethService);
        when(sendRateLimiter.isAllowed(USER_ID, 2L)).thenReturn(true);
        when(ethService.deriveAddress("pk")).thenReturn("0xUser");
        when(addressNormalizer.sameAddress(BlockchainNetwork.ETH, "0xUser", "0xUser")).thenReturn(true);
        when(assetService.getFreshBalance("0xUser", BlockchainNetwork.ETH)).thenReturn("1.0");
        when(ethService.estimateSendFee()).thenReturn(new BigDecimal("0.002"));
        when(ethService.minRetainedBalance()).thenReturn(BigDecimal.ZERO);
        when(ethService.send(eq("pk"), eq("0xDest"), any())).thenReturn("0xHASH");

        SendTransactionService.SendResult result = service.send(
                user, ethWallet, BlockchainNetwork.ETH, "0xDest", new BigDecimal("0.999"), true, "ETH", 7L);

        assertThat(result.txHash()).isEqualTo("0xHASH");

        ArgumentCaptor<BigDecimal> sent = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ethService).send(eq("pk"), eq("0xDest"), sent.capture());
        assertThat(sent.getValue()).isEqualByComparingTo("0.998");

        verify(transactionLogService).createPendingWithHash(
                eq(USER_ID), eq(2L), eq(TransactionLogEntity.TxType.SEND),
                eq(BlockchainNetwork.ETH), eq("ETH"),
                argThat(a -> a.compareTo(new BigDecimal("0.998")) == 0),
                eq("0xUser"), eq("0xDest"), eq("0xHASH"), any());
    }

    @Test
    void executeWithIdempotency_alreadyCompleted_returnsCachedWithoutBroadcast() {
        when(idempotencyStore.acquire(USER_ID, "key-1")).thenReturn(
                new IdempotencyStore.Acquired(IdempotencyStore.AcquireResult.ALREADY_COMPLETED, 42L, "CACHED_TX"));
        SendIntentService.ResolvedTransfer transfer =
                new SendIntentService.ResolvedTransfer("jti-1", TOKEN, wallet, "Tdest", new BigDecimal("10"), false);

        SendTransactionService.SendResult result = service.executeWithIdempotency(user, transfer, "key-1");

        assertThat(result.txHash()).isEqualTo("CACHED_TX");
        verify(tokenBlockchainService, never()).send(any(), any(), any());
        verify(idempotencyStore, never()).complete(anyLong(), any());
    }

    @Test
    void executeWithIdempotency_noHeader_derivesKeyFromIntentJti() {
        when(idempotencyStore.acquire(USER_ID, "jti:jti-1")).thenReturn(
                new IdempotencyStore.Acquired(IdempotencyStore.AcquireResult.ALREADY_COMPLETED, 42L, "CACHED_TX"));
        SendIntentService.ResolvedTransfer transfer =
                new SendIntentService.ResolvedTransfer("jti-1", TOKEN, wallet, "Tdest", new BigDecimal("10"), false);

        SendTransactionService.SendResult result = service.executeWithIdempotency(user, transfer, null);

        assertThat(result.txHash()).isEqualTo("CACHED_TX");
        verify(idempotencyStore).acquire(USER_ID, "jti:jti-1");
    }

    @Test
    void send_fastPath_closesIdempotencySlotInLock_withLiveHash() {
        when(sendRateLimiter.isAllowed(USER_ID, WALLET_ID)).thenReturn(true);
        when(tokenBlockchainService.deriveAddress("privkey")).thenReturn(ADDRESS);
        when(addressNormalizer.sameAddress(TOKEN, ADDRESS, ADDRESS)).thenReturn(true);
        when(assetService.getFreshBalance(ADDRESS, TOKEN)).thenReturn("100");
        when(assetService.getFreshBalance(ADDRESS, NATIVE)).thenReturn("50");
        when(tokenBlockchainService.send(any(), any(), any())).thenReturn("TX_HASH");

        service.send(user, wallet, TOKEN, "Tdest", new BigDecimal("10"), false, "USDT", 55L);

        verify(idempotencyStore).complete(55L, "TX_HASH");
    }

    @Test
    void executeWithIdempotency_persistFailsAfterBroadcast_slotNotReopened() {
        when(idempotencyStore.acquire(USER_ID, "key-9")).thenReturn(
                new IdempotencyStore.Acquired(IdempotencyStore.AcquireResult.CREATED, 99L, null));
        when(sendRateLimiter.isAllowed(USER_ID, WALLET_ID)).thenReturn(true);
        when(tokenBlockchainService.deriveAddress("privkey")).thenReturn(ADDRESS);
        when(addressNormalizer.sameAddress(TOKEN, ADDRESS, ADDRESS)).thenReturn(true);
        when(assetService.getFreshBalance(ADDRESS, TOKEN)).thenReturn("100");
        when(assetService.getFreshBalance(ADDRESS, NATIVE)).thenReturn("50");
        when(tokenBlockchainService.send(any(), any(), any())).thenReturn("TX_HASH");
        when(transactionLogService.createPendingWithHash(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        SendIntentService.ResolvedTransfer transfer =
                new SendIntentService.ResolvedTransfer("jti-9", TOKEN, wallet, "Tdest", new BigDecimal("10"), false);

        assertThatThrownBy(() -> service.executeWithIdempotency(user, transfer, "key-9"))
                .isInstanceOf(RuntimeException.class);

        verify(idempotencyStore).complete(99L, "TX_HASH");
        verify(idempotencyStore).releaseIfPending(99L);
        verify(idempotencyStore, never()).release(anyLong());
    }
}
