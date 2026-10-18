package com.example.walletbot.swap.application;

import com.example.walletbot.asset.application.AssetService;
import com.example.walletbot.asset.domain.OutgoingReservationPort;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.platform.persistence.WalletLockService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.swap.application.intent.SwapIntent;
import com.example.walletbot.swap.application.intent.SwapIntentService;
import com.example.walletbot.swap.application.policy.SwapRateLimiter;
import com.example.walletbot.swap.domain.*;
import com.example.walletbot.swap.domain.exception.*;
import com.example.walletbot.swap.provider.SwapProvider;
import com.example.walletbot.swap.routing.ProviderRegistry;
import com.example.walletbot.user.application.UserStatusGuard;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwapExecutionService {

    private final SwapIntentService intentService;
    private final SwapOrderRepository orderRepository;
    private final SwapOrderMutator orderMutator;
    private final ProviderRegistry providerRegistry;
    private final WalletService walletService;
    private final AssetService assetService;
    private final OutgoingReservationPort reservationPort;
    private final SwapRateLimiter rateLimiter;
    private final WalletLockService walletLockService;
    private final BlockchainServiceFactory blockchainFactory;
    private final UserStatusGuard userStatusGuard;

    public SwapOrderEntity execute(UserEntity user, String intentToken, String idemKey) {
        userStatusGuard.requireCanTransact(user);
        if (idemKey == null || idemKey.isBlank()) {
            throw new BadRequestException("error.idempotency_key_required");
        }
        SwapIntent intent = intentService.verify(intentToken, user.getId());

        var existing = orderRepository.findByUserIdAndIdemKey(user.getId(), idemKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        WalletEntity fromWallet = walletService.getWalletForNetwork(user, intent.fromNetwork());
        if (fromWallet == null || !fromWallet.getAddress().equals(intent.fromAddress())) {
            throw new SwapIntentInvalidException("Source wallet mismatch");
        }
        WalletEntity toWallet = walletService.getWalletForNetwork(user, intent.toNetwork());
        if (toWallet == null || !toWallet.getAddress().equals(intent.toAddress())) {
            throw new SwapIntentInvalidException("Destination wallet mismatch");
        }

        if (!rateLimiter.isAllowed(user.getId(), fromWallet.getId())) {
            throw new RateLimitException("Swap rate limit exceeded");
        }

        BigDecimal balance = balanceOf(user, fromWallet, intent.fromNetwork());
        if (balance.compareTo(intent.amountIn()) < 0) {
            throw new InsufficientFundsException("Insufficient balance at execution time");
        }

        if (!intent.fromNetwork().isToken()) {
            BigDecimal nativeFee = blockchainFactory.getService(intent.fromNetwork()).estimateSendFee();
            if (balance.compareTo(intent.amountIn().add(nativeFee)) < 0) {
                throw new SwapInsufficientGasException(
                        "Not enough " + intent.fromNetwork().getGasNetwork().getSymbol()
                                + " to cover the network fee for this swap");
            }
        } else {
            requireNativeGasForToken(user, intent.fromNetwork(), fromWallet, intent.amountIn());
        }

        WalletLockService.LockResult<SwapOrderEntity> locked = walletLockService.withLock(
                fromWallet.getId(),
                () -> orderMutator.createAuthorizedOrder(user, intent, fromWallet, toWallet, idemKey));
        if (!locked.acquired()) {
            throw new RateLimitException("Another swap on this wallet is in progress");
        }
        SwapOrderEntity order = locked.value();

        try {
            broadcast(user, order, fromWallet);
            rateLimiter.record(user.getId(), fromWallet.getId());
        } catch (SwapException e) {
            boolean sourceSide = e instanceof SwapProviderUnavailableException
                    || e instanceof SwapInsufficientGasException;
            orderMutator.markFailed(order, sourceSide
                    ? SwapOrderStatus.FAILED_SOURCE
                    : SwapOrderStatus.FAILED_PROVIDER, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error broadcasting swap order {}", order.getId(), e);
            orderMutator.markFailed(order, SwapOrderStatus.FAILED_SOURCE, "Internal error");
            throw new SwapProviderUnavailableException("Internal error during broadcast", e);
        }
        return order;
    }

    private BigDecimal balanceOf(UserEntity user, WalletEntity wallet, BlockchainNetwork network) {
        try {
            return new BigDecimal(assetService.getBalance(
                    user.getId(), wallet.getId(), wallet.getAddress(), network));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void requireNativeGasForToken(UserEntity user, BlockchainNetwork tokenNetwork,
                                          WalletEntity fromWallet, BigDecimal amountIn) {
        BlockchainNetwork gasNetwork = tokenNetwork.getGasNetwork();
        WalletEntity gasWallet = walletService.getWalletForNetwork(user, gasNetwork);
        if (gasWallet == null) {
            throw new SwapInsufficientGasException(
                    "No " + gasNetwork.getSymbol() + " wallet to cover the network fee");
        }
        BigDecimal gasBalance = balanceOf(user, gasWallet, gasNetwork);
        BigDecimal feePerTx = blockchainFactory.getService(tokenNetwork)
                .estimateNativeBurn(fromWallet.getAddress(), null, amountIn);
        if (gasBalance.compareTo(feePerTx) < 0) {
            throw new SwapInsufficientGasException(
                    "Not enough " + gasNetwork.getSymbol()
                            + " to cover the network fee for this swap");
        }
    }

    private void broadcast(UserEntity user, SwapOrderEntity order, WalletEntity fromWallet) {
        SwapProvider provider = providerRegistry.byId(order.getProvider())
                .orElseThrow(() -> new SwapQuoteUnavailableException(
                        "Provider not registered: " + order.getProvider()));

        SwapQuote q = reconstructQuote(order);

        PreparedSwap prepared = provider.prepare(q, fromWallet.getAddress(), order.getToAddress());

        orderMutator.applyUpdate(order, o -> {
            o.setProviderOrderId(prepared.providerOrderId());
            o.setDepositAddress(prepared.depositAddress());
            o.setStatus(SwapOrderStatus.BROADCASTING);
        });

        SubmittedSwap submitted = provider.submit(prepared,
                new WalletKeySource(fromWallet.getAddress(), fromWallet.getPrivateKey()));

        orderMutator.applyUpdate(order, o -> {
            o.setSourceTxHash(submitted.sourceTxHash());
            o.setStatus(SwapOrderStatus.SOURCE_CONFIRMED);
        });

        reservationPort.invalidateBaseline(fromWallet.getId(), order.getFromNetwork());

        try {
            assetService.invalidateUserBalances(order.getUserId());
        } catch (Exception e) {
            log.debug("Balance cache invalidation failed for user {} after swap: {}",
                    order.getUserId(), e.toString());
        }
    }

    private SwapQuote reconstructQuote(SwapOrderEntity order) {
        return new SwapQuote(
                order.getProvider(),
                order.getProviderQuoteId(),
                SwapPair.of(order.getFromNetwork(), order.getToNetwork()),
                order.getAmountIn(),
                order.getAmountOutExpected(),
                order.getAmountOutMin(),
                order.getRateQuoted(),
                null,
                order.getExpiresAt() != null ? order.getExpiresAt() : Instant.now().plusSeconds(600),
                Collections.emptyMap()
        );
    }

}
