package com.example.walletbot.platform.blockchain;

import com.example.walletbot.shared.constant.BlockchainNetwork;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface BlockchainService {
    BlockchainNetwork getNetwork();

    WalletKeys generateWallet();

    String getBalance(String address);

    String send(String privateKey, String toAddress, BigDecimal amount);

    boolean isValidAddress(String address);

    default BigDecimal estimateSendFee() {
        return BigDecimal.ZERO;
    }

    default BigDecimal estimateNativeBurn(String fromAddress, String toAddress, BigDecimal amount) {
        return estimateSendFee();
    }

    default BigDecimal minRetainedBalance() {
        return BigDecimal.ZERO;
    }

    default String getFeeSymbol() {
        return getNetwork().getSymbol();
    }

    default boolean isSendSupported() {
        return true;
    }

    default String sendRaw(String privateKey, String to, java.math.BigInteger valueWei,
                           String dataHex, java.math.BigInteger gasLimit) {
        throw new UnsupportedOperationException("sendRaw is not supported on " + getNetwork());
    }

    default String sendWithMemo(String privateKey, String toAddress, BigDecimal amount, String memo) {
        throw new UnsupportedOperationException("sendWithMemo is not supported on " + getNetwork());
    }

    default TxStatusResult getTxStatus(String txHash, String fromAddress) {
        throw new UnsupportedOperationException("getTxStatus is not supported on " + getNetwork());
    }

    String deriveAddress(String privateKey);

    record WalletKeys(String address, String privateKey) {
    }

    enum OnChainStatus {
        PENDING, CONFIRMED, REVERTED, NOT_FOUND
    }

    record TxStatusResult(OnChainStatus status, LocalDateTime blockTimestamp, BigDecimal feeNative) {
        public static TxStatusResult pending() {
            return new TxStatusResult(OnChainStatus.PENDING, null, null);
        }

        public static TxStatusResult notFound() {
            return new TxStatusResult(OnChainStatus.NOT_FOUND, null, null);
        }

        public static TxStatusResult confirmed(LocalDateTime blockTimestamp, BigDecimal feeNative) {
            return new TxStatusResult(OnChainStatus.CONFIRMED, blockTimestamp, feeNative);
        }

        public static TxStatusResult reverted(BigDecimal feeNative) {
            return new TxStatusResult(OnChainStatus.REVERTED, null, feeNative);
        }
    }
}