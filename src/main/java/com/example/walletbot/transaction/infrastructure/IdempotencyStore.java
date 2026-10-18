package com.example.walletbot.transaction.infrastructure;

import com.example.walletbot.transaction.domain.SendIdempotencyEntity;
import com.example.walletbot.transaction.domain.SendIdempotencyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
public class IdempotencyStore {

    private final SendIdempotencyRepository repo;

    private final TransactionTemplate requiresNewTx;

    public IdempotencyStore(SendIdempotencyRepository repo, PlatformTransactionManager txManager) {
        this.repo = repo;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Acquired acquire(Long userId, String idemKey) {
        try {
            Long rowId = requiresNewTx.execute(status -> {
                SendIdempotencyEntity entity = new SendIdempotencyEntity();
                entity.setUserId(userId);
                entity.setIdemKey(idemKey);
                return repo.saveAndFlush(entity).getId();
            });
            return new Acquired(AcquireResult.CREATED, rowId, null);
        } catch (DataIntegrityViolationException e) {
            return requiresNewTx.execute(status -> readExisting(userId, idemKey));
        }
    }

    private Acquired readExisting(Long userId, String idemKey) {
        return repo.findByUserIdAndIdemKey(userId, idemKey)
                .map(existing -> existing.getTxHash() != null
                        ? new Acquired(AcquireResult.ALREADY_COMPLETED, existing.getId(), existing.getTxHash())
                        : new Acquired(AcquireResult.IN_PROGRESS, existing.getId(), null))
                .orElseThrow(() -> new IllegalStateException(
                        "Unique conflict but row missing for user=" + userId + " key=" + idemKey));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long rowId, String txHash) {
        repo.markCompleted(rowId, txHash, Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long rowId) {
        repo.deleteById(rowId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseIfPending(Long rowId) {
        repo.deleteByIdIfPending(rowId);
    }

    public enum AcquireResult {CREATED, ALREADY_COMPLETED, IN_PROGRESS}

    public record Acquired(AcquireResult result, Long rowId, String txHash) {
    }
}
