package com.example.walletbot.deposit.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeliusAddressBackfillRunner implements ApplicationRunner {

    private final HeliusWebhookRegistrar registrar;

    @Override
    public void run(ApplicationArguments args) {
        if (!registrar.isConfigured()) {
            return;
        }
        log.info("Backfilling Solana addresses to Helius webhook");
        registrar.syncAddresses();
    }
}
