package com.example.walletbot.transaction.infrastructure;

import com.example.walletbot.transaction.domain.SendIdempotencyEntity;
import com.example.walletbot.transaction.domain.SendIdempotencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyStoreTest {

    @Mock
    SendIdempotencyRepository repo;

    @Mock
    PlatformTransactionManager txManager;

    @InjectMocks
    IdempotencyStore store;

    @Test
    void acquire_freshKey_returnsCreated() {
        SendIdempotencyEntity saved = new SendIdempotencyEntity();
        saved.setId(7L);
        when(repo.saveAndFlush(any())).thenReturn(saved);

        IdempotencyStore.Acquired acquired = store.acquire(1L, "key-1");

        assertThat(acquired.result()).isEqualTo(IdempotencyStore.AcquireResult.CREATED);
        assertThat(acquired.rowId()).isEqualTo(7L);
        assertThat(acquired.txHash()).isNull();
    }

    @Test
    void acquire_duplicateCompleted_returnsAlreadyCompleted() {
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        SendIdempotencyEntity existing = new SendIdempotencyEntity();
        existing.setId(3L);
        existing.setTxHash("0xabc");
        when(repo.findByUserIdAndIdemKey(1L, "key-1")).thenReturn(Optional.of(existing));

        IdempotencyStore.Acquired acquired = store.acquire(1L, "key-1");

        assertThat(acquired.result()).isEqualTo(IdempotencyStore.AcquireResult.ALREADY_COMPLETED);
        assertThat(acquired.txHash()).isEqualTo("0xabc");
    }

    @Test
    void acquire_duplicateInProgress_returnsInProgress() {
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        SendIdempotencyEntity existing = new SendIdempotencyEntity();
        existing.setId(3L);
        existing.setTxHash(null);
        when(repo.findByUserIdAndIdemKey(1L, "key-1")).thenReturn(Optional.of(existing));

        IdempotencyStore.Acquired acquired = store.acquire(1L, "key-1");

        assertThat(acquired.result()).isEqualTo(IdempotencyStore.AcquireResult.IN_PROGRESS);
        assertThat(acquired.txHash()).isNull();
    }

    @Test
    void acquire_uniqueConflictButRowMissing_throws() {
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        when(repo.findByUserIdAndIdemKey(1L, "key-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.acquire(1L, "key-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void complete_marksRowCompleted() {
        store.complete(5L, "0xdef");
        verify(repo).markCompleted(eq(5L), eq("0xdef"), any(Instant.class));
    }

    @Test
    void release_deletesRow() {
        store.release(5L);
        verify(repo).deleteById(5L);
    }

    @Test
    void releaseIfPending_deletesOnlyWhileStillPending() {
        store.releaseIfPending(5L);
        verify(repo).deleteByIdIfPending(5L);
    }
}
