package com.example.walletbot.transaction.infrastructure;

import com.example.walletbot.transaction.application.TxStatusService;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TxStatusPollerTest {

    @Mock
    TxStatusService txStatusService;
    @InjectMocks
    TxStatusPoller poller;

    @Test
    void poll_continuesAfterIndividualFailure() {
        TransactionLogEntity tx1 = tx(1L);
        TransactionLogEntity tx2 = tx(2L);
        TransactionLogEntity tx3 = tx(3L);
        when(txStatusService.findPendingForPolling()).thenReturn(List.of(tx1, tx2, tx3));
        doThrow(new RuntimeException("RPC down")).when(txStatusService).pollAndApply(tx2);

        poller.poll();

        verify(txStatusService).pollAndApply(tx1);
        verify(txStatusService).pollAndApply(tx2);
        verify(txStatusService).pollAndApply(tx3);
    }

    @Test
    void poll_empty_doesNothing() {
        when(txStatusService.findPendingForPolling()).thenReturn(List.of());

        poller.poll();

        verify(txStatusService, never()).pollAndApply(any());
    }

    private TransactionLogEntity tx(long id) {
        TransactionLogEntity tx = new TransactionLogEntity();
        tx.setId(id);
        return tx;
    }
}
