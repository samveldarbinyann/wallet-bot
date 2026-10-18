package com.example.walletbot.transaction.api.dto;

import com.example.walletbot.transaction.domain.HistoryItem;

import java.util.List;

public record TransactionHistoryResponse(
        List<HistoryItem> content,
        int totalPages,
        long totalElements,
        int page,
        int size
) {
}
