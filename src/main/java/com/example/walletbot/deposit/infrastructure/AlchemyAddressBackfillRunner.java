package com.example.walletbot.deposit.infrastructure;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlchemyAddressBackfillRunner implements ApplicationRunner {

    private static final int BATCH_SIZE = 500;

    private final AlchemyWebhookRegistrar registrar;
    private final WalletRepository walletRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (!registrar.isConfigured()) {
            return;
        }
        List<String> addresses = walletRepository.findAddressesByNetwork(BlockchainNetwork.ETH);
        if (addresses.isEmpty()) {
            return;
        }
        log.info("Backfilling {} EVM address(es) to Alchemy webhooks", addresses.size());
        for (int i = 0; i < addresses.size(); i += BATCH_SIZE) {
            registrar.addAddresses(addresses.subList(i, Math.min(i + BATCH_SIZE, addresses.size())));
        }
    }
}