package com.example.walletbot.transaction.application;

import com.example.walletbot.addressbook.application.AddressBookService;
import com.example.walletbot.asset.application.AssetService;
import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.platform.persistence.WalletLockService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.shared.util.AddressNormalizer;
import com.example.walletbot.transaction.domain.NetworkFee;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import com.example.walletbot.transaction.infrastructure.IdempotencyStore;
import com.example.walletbot.user.application.UserStatusGuard;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendTransactionService {
    private final AssetService assetService;
    private final BlockchainServiceFactory blockchainFactory;
    private final PriceService priceService;
    private final TransactionLogService transactionLogService;
    private final SendRateLimiter sendRateLimiter;
    private final AddressBookService addressBookService;
    private final AddressNormalizer addressNormalizer;
    private final IdempotencyStore idempotencyStore;
    private final UserStatusGuard userStatusGuard;
    private final WalletLockService walletLockService;
    private final OutgoingReservationService reservationService;

    SendResult send(UserEntity user, WalletEntity wallet,
                    BlockchainNetwork net, String toAddress,
                    BigDecimal amount, boolean max, String displaySymbol, Long idemRowId) {

        userStatusGuard.requireCanTransact(user);

        WalletLockService.LockResult<SendResult> locked = walletLockService.withLock(
                wallet.getId(),
                () -> sendUnderLock(user, wallet, net, toAddress, amount, max, displaySymbol, idemRowId));
        if (!locked.acquired()) {
            throw new RateLimitException(String.format(
                    "Another operation on this wallet is in progress: user=%s wallet=%s net=%s",
                    user.getId(), wallet.getId(), net));
        }
        SendResult result = locked.value();
        sendRateLimiter.recordSend(user.getId(), wallet.getId());
        assetService.invalidateUserBalances(user.getId());
        addressBookService.recordUsage(user.getId(), net, toAddress);
        return result;
    }

    private SendResult sendUnderLock(UserEntity user, WalletEntity wallet, BlockchainNetwork net,
                                     String toAddress, BigDecimal amount, boolean max, String displaySymbol,
                                     Long idemRowId) {
        if (!sendRateLimiter.isAllowed(user.getId(), wallet.getId())) {
            throw new RateLimitException(String.format(
                    "Rate limit exceeded: user=%s wallet=%s net=%s", user.getId(), wallet.getId(), net));
        }

        BlockchainService service = blockchainFactory.getService(net);
        String derived;
        try {
            derived = service.deriveAddress(wallet.getPrivateKey());
        } catch (Exception e) {
            log.error("Key integrity check failed for wallet {}: {}",
                    wallet.getId(), e.getClass().getSimpleName());
            throw new IllegalStateException("Wallet key integrity failure");
        }
        if (!addressNormalizer.sameAddress(net, derived, wallet.getAddress())) {
            log.error("Wallet address mismatch: stored={} derived={} walletId={}",
                    wallet.getAddress(), derived, wallet.getId());
            throw new IllegalStateException("Wallet address mismatch");
        }

        BigDecimal current = new BigDecimal(
                assetService.getFreshBalance(wallet.getAddress(), net));

        BigDecimal available = reservationService.effectiveAvailable(wallet.getId(), net, current);

        BigDecimal sendAmount = amount;
        if (!net.isToken()) {
            BigDecimal feeNative = service.estimateSendFee();
            BigDecimal retained = service.minRetainedBalance();
            if (max) {
                sendAmount = available.subtract(feeNative).subtract(retained)
                        .setScale(net.getDecimals(), RoundingMode.DOWN);
            }
            if (sendAmount.signum() <= 0 || available.compareTo(sendAmount.add(feeNative)) < 0) {
                throw new InsufficientFundsException(String.format(
                        "Insufficient balance (amount + network fee exceeds balance): user=%s wallet=%s net=%s "
                                + "max=%s current=%s available=%s sendAmount=%s feeNative=%s retained=%s",
                        user.getId(), wallet.getId(), net, max, current, available,
                        sendAmount, feeNative, retained));
            }
        } else {
            if (max) {
                sendAmount = available.setScale(net.getDecimals(), RoundingMode.DOWN);
            }
            if (sendAmount.signum() <= 0 || available.compareTo(sendAmount) < 0) {
                throw new InsufficientFundsException(String.format(
                        "Insufficient token balance for amount: user=%s wallet=%s net=%s max=%s current=%s "
                                + "available=%s sendAmount=%s",
                        user.getId(), wallet.getId(), net, max, current, available, sendAmount));
            }
            BigDecimal nativeBalance = new BigDecimal(
                    assetService.getFreshBalance(wallet.getAddress(), net.getGasNetwork()));
            BigDecimal feePerTx = service.estimateNativeBurn(wallet.getAddress(), toAddress, sendAmount);
            if (nativeBalance.compareTo(feePerTx) < 0) {
                throw new InsufficientFundsException(String.format(
                        "Insufficient %s balance for the network fee: user=%s wallet=%s net=%s gasNet=%s "
                                + "nativeBalance=%s feePerTx=%s",
                        net.getGasNetwork().getSymbol(), user.getId(), wallet.getId(), net,
                        net.getGasNetwork(), nativeBalance, feePerTx));
            }
        }

        String txHash = service.send(wallet.getPrivateKey(), toAddress, sendAmount);
        if (txHash == null) {
            throw new IllegalStateException("No txHash returned");
        }
        idempotencyStore.complete(idemRowId, txHash);
        TransactionLogEntity row = transactionLogService.createPendingWithHash(
                user.getId(), wallet.getId(), TransactionLogEntity.TxType.SEND,
                net, displaySymbol, sendAmount, wallet.getAddress(), toAddress, txHash, null
        );
        reservationService.recordBroadcastBaseline(wallet.getId(), net, current);
        return SendResult.broadcast(row.getId(), txHash);
    }

    public EstimateResult buildEstimate(UserEntity user, WalletEntity wallet, BlockchainNetwork net,
                                        BigDecimal amount, boolean max) {
        userStatusGuard.requireCanTransact(user);
        String balanceStr = assetService.getBalance(user.getId(), wallet.getId(), wallet.getAddress(), net);
        BigDecimal balance = reservationService.effectiveAvailable(wallet.getId(), net, new BigDecimal(balanceStr));

        BlockchainService service = blockchainFactory.getService(net);
        BigDecimal feeNative = service.estimateNativeBurn(wallet.getAddress(), null, amount);
        String feeSymbol = service.getFeeSymbol();

        BigDecimal maxSendable;
        boolean hasEnoughGas;
        BigDecimal gasShortfall;

        if (net.isToken()) {
            maxSendable = balance.max(BigDecimal.ZERO).setScale(8, RoundingMode.DOWN);
            BigDecimal nativeBalance = new BigDecimal(
                    assetService.getBalance(user.getId(), wallet.getId(), wallet.getAddress(), net.getGasNetwork()));
            hasEnoughGas = nativeBalance.compareTo(feeNative) >= 0;
            gasShortfall = hasEnoughGas ? BigDecimal.ZERO
                    : feeNative.subtract(nativeBalance).setScale(8, RoundingMode.UP);
        } else {
            BigDecimal retained = service.minRetainedBalance();
            BigDecimal afterGas = balance.subtract(feeNative).subtract(retained).max(BigDecimal.ZERO);
            maxSendable = afterGas.setScale(8, RoundingMode.DOWN);
            hasEnoughGas = balance.compareTo(feeNative) >= 0;
            gasShortfall = hasEnoughGas ? BigDecimal.ZERO
                    : feeNative.subtract(balance).setScale(8, RoundingMode.UP);
        }

        BigDecimal feeAmount = feeNative.setScale(8, RoundingMode.HALF_UP);
        BigDecimal usd = priceService.usdValue(feeSymbol, feeAmount);
        NetworkFee networkFee = new NetworkFee(feeAmount, feeSymbol, usd);

        BigDecimal recipientAmount = max ? maxSendable : amount;
        return new EstimateResult(networkFee, maxSendable, hasEnoughGas, gasShortfall, recipientAmount);
    }

    public SendResult executeWithIdempotency(UserEntity user, SendIntentService.ResolvedTransfer transfer,
                                             String idemKeyHeader) {
        return executeWithIdempotency(user, transfer.wallet(), transfer.net(), transfer.canonicalTo(),
                transfer.amount(), transfer.max(), resolveIdemKey(idemKeyHeader, transfer.jti()));
    }

    private static String resolveIdemKey(String header, String intentJti) {
        if (header != null && !header.isBlank()) {
            return header;
        }
        if (intentJti != null && !intentJti.isBlank()) {
            return "jti:" + intentJti;
        }
        throw new BadRequestException("error.idempotency_key_required");
    }

    private SendResult executeWithIdempotency(UserEntity user, WalletEntity wallet, BlockchainNetwork net,
                                              String canonicalTo, BigDecimal amount, boolean max, String idemKey) {
        if (idemKey == null || idemKey.isBlank()) {
            throw new BadRequestException("error.idempotency_key_required");
        }
        if (idemKey.length() > 80) {
            throw new BadRequestException("error.idempotency_key_too_long");
        }

        IdempotencyStore.Acquired acquired = idempotencyStore.acquire(user.getId(), idemKey);
        switch (acquired.result()) {
            case ALREADY_COMPLETED -> {
                Long txId = transactionLogService.findIdByWalletAndHash(
                        wallet.getId(), acquired.txHash());
                return SendResult.broadcast(txId, acquired.txHash());
            }
            case IN_PROGRESS -> throw new RateLimitException(
                    "Another transaction with this Idempotency-Key is still in progress");
            case CREATED -> {  }
        }

        try {
            return send(user, wallet, net, canonicalTo,
                    amount, max, net.getAsset().getSymbol(), acquired.rowId());
        } catch (RuntimeException e) {
            idempotencyStore.releaseIfPending(acquired.rowId());
            throw e;
        }
    }

    public record SendResult(String txHash, Long txId) {
        public static SendResult broadcast(Long txId, String txHash) {
            return new SendResult(txHash, txId);
        }
    }

    public record EstimateResult(
            NetworkFee networkFee,
            BigDecimal maxSendable,
            boolean hasEnoughGas,
            BigDecimal gasShortfall,
            BigDecimal recipientAmount
    ) {
    }
}
