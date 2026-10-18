package com.example.walletbot.transaction.application;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.transaction.domain.HistoryItem;
import com.example.walletbot.transaction.domain.TransactionDto;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryServiceTest {

    private static final long USER = 7L;

    private TransactionLogRepository txRepo;
    private SwapOrderRepository swapRepo;
    private TransactionLogMapper mapper;
    private PriceService priceService;
    private HistoryService service;

    @BeforeEach
    void setUp() {
        txRepo = mock(TransactionLogRepository.class);
        swapRepo = mock(SwapOrderRepository.class);
        mapper = mock(TransactionLogMapper.class);
        priceService = mock(PriceService.class);
        service = new HistoryService(txRepo, swapRepo, mapper, priceService);

        when(priceService.getUsdPrices(any())).thenReturn(Map.of());
        when(mapper.toDto(any(TransactionLogEntity.class), any(BigDecimal.class)))
                .thenAnswer(inv -> {
                    TransactionLogEntity e = inv.getArgument(0);
                    return new TransactionDto(String.valueOf(e.getId()), "OUT", e.getAmount(),
                            e.getSymbol(), null, "COMPLETED", null, e.getTxHash(), "ts", BigDecimal.ZERO);
                });
    }

    private TransactionLogEntity tx(long id, long epochSecond) {
        TransactionLogEntity e = new TransactionLogEntity();
        e.setId(id);
        e.setUserId(USER);
        e.setType(TransactionLogEntity.TxType.SEND);
        e.setNetwork(BlockchainNetwork.ETH);
        e.setSymbol("ETH");
        e.setAmount(BigDecimal.ONE);
        e.setStatus(TransactionLogEntity.TxStatus.SUCCESS);
        e.setTxTimestamp(LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC));
        return e;
    }

    private SwapOrderEntity swap(long id, long epochSecond, String sourceTx, String destTx) {
        SwapOrderEntity o = new SwapOrderEntity();
        o.setId(id);
        o.setUserId(USER);
        o.setFromNetwork(BlockchainNetwork.USDT_TRC20);
        o.setToNetwork(BlockchainNetwork.ETH);
        o.setAmountIn(new BigDecimal("100"));
        o.setAmountOutExpected(new BigDecimal("0.03"));
        o.setStatus(SwapOrderStatus.COMPLETED);
        o.setSourceTxHash(sourceTx);
        o.setDestTxHash(destTx);
        o.setCreatedAt(Instant.ofEpochSecond(epochSecond));
        return o;
    }

    @Test
    void mergesSwapsAndTransactionsSortedByTimeDescending() {
        when(swapRepo.findDisplayableByUserId(eq(USER), any(), any()))
                .thenReturn(List.of(swap(1L, 200, null, null)));
        when(txRepo.findByUserIdOrderByDisplayTimeDesc(eq(USER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx(10L, 100)), PageRequest.of(0, 10), 1));

        Page<HistoryItem> result = service.findUnifiedHistory(USER, 0, 10);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).kind()).isEqualTo(HistoryItem.KIND_SWAP);
        assertThat(result.getContent().get(1).kind()).isEqualTo(HistoryItem.KIND_TRANSACTION);
    }

    @Test
    void excludesTransactionsThatBelongToSwaps() {
        when(swapRepo.findDisplayableByUserId(eq(USER), any(), any()))
                .thenReturn(List.of(swap(1L, 200, "0xsrc", "0xdest")));
        when(txRepo.findRecentExcludingTxHashes(eq(USER), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(txRepo.countExcludingTxHashes(eq(USER), any())).thenReturn(0L);

        Page<HistoryItem> result = service.findUnifiedHistory(USER, 0, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> hashes = ArgumentCaptor.forClass(Collection.class);
        verify(txRepo).findRecentExcludingTxHashes(eq(USER), hashes.capture(), any(Pageable.class));
        assertThat(hashes.getValue()).containsExactlyInAnyOrder("0xsrc", "0xdest");
        verify(txRepo, never()).findByUserIdOrderByDisplayTimeDesc(anyLong(), any(Pageable.class));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).kind()).isEqualTo(HistoryItem.KIND_SWAP);
    }

    @Test
    void paginatesMergedStream() {
        when(swapRepo.findDisplayableByUserId(eq(USER), any(), any()))
                .thenReturn(List.of(swap(1L, 300, null, null), swap(2L, 50, null, null)));
        when(txRepo.findByUserIdOrderByDisplayTimeDesc(eq(USER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx(90L, 90), tx(80L, 80), tx(70L, 70)),
                        PageRequest.of(0, 4), 3));

        Page<HistoryItem> result = service.findUnifiedHistory(USER, 1, 2);

        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).transaction().id()).isEqualTo("80");
        assertThat(result.getContent().get(1).transaction().id()).isEqualTo("70");
    }
}
