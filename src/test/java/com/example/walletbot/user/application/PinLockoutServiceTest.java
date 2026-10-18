package com.example.walletbot.user.application;

import com.example.walletbot.user.domain.UserRepository;
import com.example.walletbot.user.infrastructure.UserCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PinLockoutServiceTest {

    @Mock
    UserRepository userRepo;
    @Mock
    UserCacheService userCacheService;

    @InjectMocks
    PinLockoutService service;

    @Test
    void recordFailure_incrementsCountAndEvictsCache() {
        Instant before = Instant.now();

        Instant lockUntil = service.recordFailure(42L);

        assertThat(lockUntil).isAfter(before);
        verify(userRepo).incrementPinFailures(eq(42L), eq(PinLockoutService.MAX_PIN_ATTEMPTS), any(Instant.class));
        verify(userCacheService).evict(42L);
    }

    @Test
    void recordFailure_returnsLockoutAboutFifteenMinutesAhead() {
        Instant lockUntil = service.recordFailure(1L);

        long minutesAhead = Duration.between(Instant.now(), lockUntil).toMinutes();
        assertThat(minutesAhead).isBetween(14L, 15L);
    }

    @Test
    void resetLockout_whenRowUpdated_evictsCache() {
        when(userRepo.resetPinFailures(7L)).thenReturn(1);

        service.resetLockout(7L);

        verify(userCacheService).evict(7L);
    }

    @Test
    void resetLockout_whenNoRowUpdated_doesNotEvict() {
        when(userRepo.resetPinFailures(7L)).thenReturn(0);

        service.resetLockout(7L);

        verify(userCacheService, never()).evict(any());
    }
}
