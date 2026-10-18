package com.example.walletbot.platform.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonitoringService {

    private final Counter authFailedCounter;
    private final Counter transactionSentCounter;
    private final Counter pinVerificationFailedCounter;
    private final Counter otpVerificationFailedCounter;
    private final Counter notificationSentCounter;

    private final Counter exportRequestedCounter;
    private final Counter exportConfirmedCounter;
    private final Counter exportFailedOtpCounter;
    private final Counter exportFailedBurnedCounter;
    private final Counter exportFailedRateLimitCounter;
    private final Counter exportFailedTokenInvalidCounter;
    private final MeterRegistry registry;
    private final Map<String, Counter> exportRevealedByNetwork = new ConcurrentHashMap<>();

    public MonitoringService(MeterRegistry registry) {
        this.registry = registry;

        this.authFailedCounter = Counter.builder("security.auth.failed")
                .description("Total number of failed authentication attempts")
                .register(registry);

        this.transactionSentCounter = Counter.builder("transaction.sent")
                .description("Total number of successful transactions sent")
                .register(registry);

        this.pinVerificationFailedCounter = Counter.builder("security.pin.failed")
                .description("Total number of failed PIN verification attempts")
                .register(registry);

        this.otpVerificationFailedCounter = Counter.builder("security.otp.failed")
                .description("Total number of failed OTP verification attempts")
                .register(registry);

        this.notificationSentCounter = Counter.builder("notification.sent")
                .description("Total notifications sent")
                .register(registry);

        this.exportRequestedCounter = Counter.builder("security.export.requested")
                .description("Export key sessions initiated")
                .register(registry);

        this.exportConfirmedCounter = Counter.builder("security.export.confirmed")
                .description("Export key sessions confirmed")
                .register(registry);

        this.exportFailedOtpCounter = Counter.builder("security.export.failed.otp")
                .description("Export key sessions failed due to wrong OTP")
                .register(registry);

        this.exportFailedBurnedCounter = Counter.builder("security.export.failed.burned")
                .description("Export key sessions rejected due to burned JTI/SID")
                .register(registry);

        this.exportFailedRateLimitCounter = Counter.builder("security.export.failed.ratelimit")
                .description("Export key requests rejected by rate limiter")
                .register(registry);

        this.exportFailedTokenInvalidCounter = Counter.builder("security.export.failed.token_invalid")
                .description("Export key requests rejected due to invalid/expired/forged JWT")
                .register(registry);
    }

    public void incrementAuthFailed() {
        authFailedCounter.increment();
    }

    public void incrementTransactionSent() {
        transactionSentCounter.increment();
    }

    public void incrementPinVerificationFailed() {
        pinVerificationFailedCounter.increment();
    }

    public void incrementOtpVerificationFailed() {
        otpVerificationFailedCounter.increment();
    }

    public void incrementNotificationSent() {
        notificationSentCounter.increment();
    }

    public void incrementExportRequested() {
        exportRequestedCounter.increment();
    }

    public void incrementExportConfirmed() {
        exportConfirmedCounter.increment();
    }

    public void incrementExportRevealedFor(String network) {
        exportRevealedByNetwork.computeIfAbsent(network, n ->
                Counter.builder("security.export.revealed")
                        .tag("network", n)
                        .description("Private keys revealed by network")
                        .register(registry)).increment();
    }

    public void incrementExportFailedOtp() {
        exportFailedOtpCounter.increment();
    }

    public void incrementExportFailedBurned() {
        exportFailedBurnedCounter.increment();
    }

    public void incrementExportFailedRateLimit() {
        exportFailedRateLimitCounter.increment();
    }

    public void incrementExportFailedTokenInvalid() {
        exportFailedTokenInvalidCounter.increment();
    }
}