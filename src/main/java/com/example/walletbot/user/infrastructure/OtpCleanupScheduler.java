package com.example.walletbot.user.infrastructure;

import com.example.walletbot.user.application.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OtpCleanupScheduler {

    private final OtpService otpService;

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanExpired() {
        otpService.purgeStale();
    }
}