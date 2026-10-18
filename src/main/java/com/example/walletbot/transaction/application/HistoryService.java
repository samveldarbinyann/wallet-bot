package com.example.walletbot.transaction.application;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.shared.exception.NotFoundException;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.transaction.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private static final int MAX_PAGE_SIZE = 20;
    private static final int DEFAULT_PAGE_SIZE = 5;

    private static final List<SwapOrderStatus> HIDDEN_SWAP_STATUSES =
            List.of(SwapOrderStatus.QUOTED, SwapOrderStatus.AUTHORIZED);

    private static final List<SwapOrderStatus> HIDDEN_FAILURES_WHEN_NO_ONCHAIN_ACTION =
            List.of(SwapOrderStatus.FAILED_SOURCE, SwapOrderStatus.EXPIRED, SwapOrderStatus.REJECTED);

    private final TransactionLogRepository transactionLogRepository;
    private final SwapOrderRepository swapOrderRepository;
    private final TransactionLogMapper transactionLogMapper;
    private final PriceService priceService;

    private static String uiStatus(SwapOrderStatus status) {
        return switch (status) {
            case COMPLETED -> "COMPLETED";
            case REFUNDED -> "REFUNDED";
            case REJECTED, EXPIRED, FAILED_SOURCE, FAILED_PROVIDER -> "FAILED";
            default -> "PENDING";
        };
    }

    private static int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public Page<HistoryItem> findUnifiedHistory(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizePageSize(size);
        int window = (safePage + 1) * safeSize;

        List<SwapOrderEntity> swaps = swapOrderRepository.findDisplayableByUserId(
                userId, HIDDEN_SWAP_STATUSES, HIDDEN_FAILURES_WHEN_NO_ONCHAIN_ACTION);
        Set<String> swapTxHashes = swaps.stream()
                .flatMap(o -> Stream.of(o.getSourceTxHash(), o.getDestTxHash()))
                .filter(h -> h != null && !h.isBlank())
                .collect(Collectors.toSet());

        List<TransactionLogEntity> txRows;
        long txCount;
        if (swapTxHashes.isEmpty()) {
            Page<TransactionLogEntity> txPage = transactionLogRepository
                    .findByUserIdOrderByDisplayTimeDesc(userId, PageRequest.of(0, window));
            txRows = txPage.getContent();
            txCount = txPage.getTotalElements();
        } else {
            txRows = transactionLogRepository.findRecentExcludingTxHashes(
                    userId, swapTxHashes, PageRequest.of(0, window));
            txCount = transactionLogRepository.countExcludingTxHashes(userId, swapTxHashes);
        }

        Map<String, BigDecimal> prices = priceService.getUsdPrices(txRows.stream()
                .map(TransactionLogEntity::getSymbol)
                .collect(Collectors.toSet()));

        List<TimedItem> items = new ArrayList<>(txRows.size() + swaps.size());
        for (TransactionLogEntity tx : txRows) {
            BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
            BigDecimal usd = prices.getOrDefault(tx.getSymbol(), BigDecimal.ZERO).multiply(amount);
            items.add(new TimedItem(
                    tx.getDisplayTime().toInstant(ZoneOffset.UTC),
                    HistoryItem.ofTransaction(transactionLogMapper.toDto(tx, usd))));
        }
        for (SwapOrderEntity swap : swaps) {
            items.add(new TimedItem(swap.getCreatedAt(), HistoryItem.ofSwap(toSwapDto(swap))));
        }
        items.sort(Comparator.comparing(TimedItem::time).reversed());

        long total = txCount + swaps.size();
        int from = Math.min(safePage * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        List<HistoryItem> content = items.subList(from, to).stream()
                .map(TimedItem::item)
                .toList();
        return new PageImpl<>(content, PageRequest.of(safePage, safeSize), total);
    }

    public TransactionDto getTransaction(Long userId, Long txId) {
        TransactionLogEntity tx = transactionLogRepository.findById(txId)
                .filter(t -> userId.equals(t.getUserId()))
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
        BigDecimal usd = BigDecimal.ZERO;
        if (tx.getSymbol() != null) {
            usd = priceService.getUsdPrices(Set.of(tx.getSymbol()))
                    .getOrDefault(tx.getSymbol(), BigDecimal.ZERO).multiply(amount);
        }
        return transactionLogMapper.toDto(tx, usd);
    }

    private SwapHistoryDto toSwapDto(SwapOrderEntity o) {
        boolean settled = o.getAmountOutActual() != null;
        BigDecimal amountOut = settled ? o.getAmountOutActual() : o.getAmountOutExpected();
        return new SwapHistoryDto(
                String.valueOf(o.getId()),
                uiStatus(o.getStatus()),
                o.getStatusReason(),
                o.getFromNetwork().getSymbol(),
                o.getToNetwork().getSymbol(),
                o.getFromNetwork().name(),
                o.getToNetwork().name(),
                o.getAmountIn(),
                amountOut,
                settled,
                o.getSourceTxHash(),
                o.getDestTxHash(),
                o.getCreatedAt().toString());
    }

    private record TimedItem(Instant time, HistoryItem item) {
    }
}
