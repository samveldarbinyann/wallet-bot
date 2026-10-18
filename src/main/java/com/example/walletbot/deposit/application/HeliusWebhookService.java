package com.example.walletbot.deposit.application;

import com.example.walletbot.platform.blockchain.config.BlockchainProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeliusWebhookService {

    private static final int LAMPORT_DECIMALS = 9;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
    private final WalletRepository walletRepository;
    private final TransactionLogService transactionLogService;
    private final BlockchainProperties blockchain;

    public void process(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Helius webhook: unparseable body: {}", e.getMessage());
            return;
        }
        if (!root.isArray()) {
            return;
        }
        for (JsonNode tx : root) {
            try {
                processTransaction(tx);
            } catch (Exception e) {
                log.warn("Helius webhook: failed to process tx {}: {}",
                        tx.path("signature").asText(null), e.getMessage());
            }
        }
    }

    private void processTransaction(JsonNode tx) {
        JsonNode error = tx.path("transactionError");
        if (!error.isNull() && !error.isMissingNode()) {
            return;
        }
        String signature = tx.path("signature").asText(null);
        if (signature == null || signature.isBlank()) {
            return;
        }
        LocalDateTime timestamp = parseTimestamp(tx.path("timestamp"));

        for (JsonNode transfer : tx.path("nativeTransfers")) {
            creditNative(transfer, signature, timestamp);
        }
        for (JsonNode transfer : tx.path("tokenTransfers")) {
            creditToken(transfer, signature, timestamp);
        }
    }

    private void creditNative(JsonNode transfer, String signature, LocalDateTime timestamp) {
        String to = transfer.path("toUserAccount").asText(null);
        WalletEntity wallet = solWallet(to);
        if (wallet == null) {
            return;
        }
        JsonNode amount = transfer.path("amount");
        if (!amount.isNumber()) {
            return;
        }
        credit(wallet, BlockchainNetwork.SOL,
                new BigDecimal(amount.bigIntegerValue()).movePointLeft(LAMPORT_DECIMALS),
                transfer.path("fromUserAccount").asText(null), to, signature, timestamp);
    }

    private void creditToken(JsonNode transfer, String signature, LocalDateTime timestamp) {
        String to = transfer.path("toUserAccount").asText(null);
        WalletEntity wallet = solWallet(to);
        if (wallet == null) {
            return;
        }
        BlockchainNetwork network = tokenNetwork(transfer.path("mint").asText(null));
        if (network == null) {
            return;
        }
        JsonNode amount = transfer.path("tokenAmount");
        if (!amount.isNumber()) {
            return;
        }
        credit(wallet, network, amount.decimalValue(),
                transfer.path("fromUserAccount").asText(null), to, signature, timestamp);
    }

    private void credit(WalletEntity wallet, BlockchainNetwork network, BigDecimal amount,
                        String from, String to, String signature, LocalDateTime timestamp) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        if (transactionLogService.existsByWalletIdAndTxHash(wallet.getId(), signature)) {
            return;
        }
        transactionLogService.saveDepositSuccess(
                wallet.getUserEntity().getId(), wallet.getId(),
                network, network.getSymbol(), amount, from, to, signature, timestamp);
    }

    private WalletEntity solWallet(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return walletRepository.findByAddressIgnoreCaseAndNetwork(address, BlockchainNetwork.SOL).orElse(null);
    }

    private BlockchainNetwork tokenNetwork(String mint) {
        if (mint == null || mint.isBlank()) {
            return null;
        }
        BlockchainProperties.Token token = blockchain.token();
        if (mint.equals(token.usdtSolMint())) {
            return BlockchainNetwork.USDT_SOL;
        }
        if (mint.equals(token.usdcSolMint())) {
            return BlockchainNetwork.USDC_SOL;
        }
        return null;
    }

    private LocalDateTime parseTimestamp(JsonNode node) {
        if (!node.isNumber()) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(node.asLong()), ZoneOffset.UTC);
    }
}
