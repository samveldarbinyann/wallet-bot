package com.example.walletbot.user.application;

import com.example.walletbot.user.domain.UserRepository;
import com.example.walletbot.user.infrastructure.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class PinLockoutService {

    public static final int MAX_PIN_ATTEMPTS = 5;
    public static final long PIN_LOCKOUT_MINUTES = 15;

    private final UserRepository userRepo;
    private final UserCacheService userCacheService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Instant recordFailure(Long userId) {
        Instant lockUntil = Instant.now().plus(PIN_LOCKOUT_MINUTES, ChronoUnit.MINUTES);
        userRepo.incrementPinFailures(userId, MAX_PIN_ATTEMPTS, lockUntil);
        userCacheService.evict(userId);
        return lockUntil;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetLockout(Long userId) {
        if (userRepo.resetPinFailures(userId) > 0) {
            userCacheService.evict(userId);
        }
    }
}
