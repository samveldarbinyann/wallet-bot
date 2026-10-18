package com.example.walletbot.platform.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringServiceTest {

    private SimpleMeterRegistry registry;
    private MonitoringService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new MonitoringService(registry);
    }

    @Test
    void incrementAuthFailed_bumpsCounter() {
        service.incrementAuthFailed();
        service.incrementAuthFailed();

        assertThat(registry.get("security.auth.failed").counter().count()).isEqualTo(2.0);
    }

    @Test
    void incrementTransactionSent_bumpsCounter() {
        service.incrementTransactionSent();
        assertThat(registry.get("transaction.sent").counter().count()).isEqualTo(1.0);
    }

    @Test
    void incrementPinAndOtpFailed_bumpSeparateCounters() {
        service.incrementPinVerificationFailed();
        service.incrementOtpVerificationFailed();

        assertThat(registry.get("security.pin.failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("security.otp.failed").counter().count()).isEqualTo(1.0);
    }

    @Test
    void incrementExportRevealedFor_taggedByNetwork() {
        service.incrementExportRevealedFor("ETH");

        assertThat(registry.get("security.export.revealed").tag("network", "ETH").counter().count())
                .isEqualTo(1.0);
    }
}
