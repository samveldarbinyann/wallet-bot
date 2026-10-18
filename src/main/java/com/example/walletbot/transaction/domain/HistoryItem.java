package com.example.walletbot.transaction.domain;

public record HistoryItem(
        String kind,
        TransactionDto transaction,
        SwapHistoryDto swap
) {
    public static final String KIND_TRANSACTION = "TRANSACTION";
    public static final String KIND_SWAP = "SWAP";

    public static HistoryItem ofTransaction(TransactionDto transaction) {
        return new HistoryItem(KIND_TRANSACTION, transaction, null);
    }

    public static HistoryItem ofSwap(SwapHistoryDto swap) {
        return new HistoryItem(KIND_SWAP, null, swap);
    }
}
