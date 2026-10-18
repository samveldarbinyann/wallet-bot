package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.application.scanner.TonDepositScanner;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.util.AddressNormalizer;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class TonApiWebhookService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WalletRepository walletRepository;
    private final TonDepositScanner tonDepositScanner;
    private final AddressNormalizer addressNormalizer;
    private final Executor ioExecutor;

    public TonApiWebhookService(WalletRepository walletRepository,
                                TonDepositScanner tonDepositScanner,
                                AddressNormalizer addressNormalizer,
                                @Qualifier("ioExecutor") Executor ioExecutor) {
        this.walletRepository = walletRepository;
        this.tonDepositScanner = tonDepositScanner;
        this.addressNormalizer = addressNormalizer;
        this.ioExecutor = ioExecutor;
    }

    public void process(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("TonAPI webhook: unparseable body: {}", e.getMessage());
            return;
        }
        Set<Long> scanned = new HashSet<>();
        if (root.isArray()) {
            for (JsonNode event : root) {
                scan(event.path("account_id").asText(null), scanned);
            }
        } else {
            scan(root.path("account_id").asText(null), scanned);
        }
    }

    private void scan(String accountId, Set<Long> scanned) {
        WalletEntity wallet = resolveWallet(accountId);
        if (wallet == null || !scanned.add(wallet.getId())) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                tonDepositScanner.scan(wallet);
            } catch (Exception e) {
                log.warn("TonAPI webhook: scan failed for wallet {}: {}", wallet.getId(), e.getMessage());
            }
        });
    }

    private WalletEntity resolveWallet(String accountId) {
        String normalized;
        try {
            normalized = addressNormalizer.canonical(BlockchainNetwork.TON, accountId);
        } catch (BadRequestException e) {
            return null;
        }
        if (normalized == null) {
            return null;
        }
        return walletRepository
                .findByAddressIgnoreCaseAndNetwork(normalized, BlockchainNetwork.TON)
                .orElse(null);
    }
}
