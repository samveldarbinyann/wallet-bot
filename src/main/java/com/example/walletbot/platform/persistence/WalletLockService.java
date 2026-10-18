package com.example.walletbot.platform.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Slf4j
@Service
public class WalletLockService {

    @PersistenceContext
    private EntityManager entityManager;

    private static long walletKey(long walletId) {
        long namespace = 0x77616C6CL;
        return (namespace << 32) | (walletId & 0xFFFFFFFFL);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> LockResult<T> withLock(long walletId, Supplier<T> body) {
        Object acquired = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(:k)")
                .setParameter("k", walletKey(walletId))
                .getSingleResult();
        if (!Boolean.TRUE.equals(acquired)) {
            return LockResult.notAcquired();
        }
        T value = body.get();
        return LockResult.acquired(value);
    }

    public record LockResult<T>(boolean acquired, T value) {
        public static <T> LockResult<T> acquired(T v) {
            return new LockResult<>(true, v);
        }

        public static <T> LockResult<T> notAcquired() {
            return new LockResult<>(false, null);
        }
    }
}
