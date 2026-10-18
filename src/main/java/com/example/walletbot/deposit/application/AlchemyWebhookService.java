package com.example.walletbot.deposit.application;

import com.example.walletbot.platform.blockchain.config.BlockchainProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.util.HexUtils;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlchemyWebhookService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WalletRepository walletRepository;
    private final TransactionLogService transactionLogService;
    private final BlockchainProperties blockchain;

    private static LocalDateTime parseCreatedAt(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(createdAt), ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    public void process(BlockchainNetwork chain, byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Alchemy webhook: unparseable body: {}", e.getMessage());
            return;
        }
        JsonNode activity = root.path("event").path("activity");
        if (!activity.isArray()) {
            return;
        }
        LocalDateTime timestamp = parseCreatedAt(root.path("createdAt").asText(null));
        for (JsonNode item : activity) {
            try {
                creditActivity(chain, item, timestamp);
            } catch (Exception e) {
                log.warn("Alchemy webhook: failed to process activity {}: {}",
                        item.path("hash").asText(null), e.getMessage());
            }
        }
    }

    private void creditActivity(BlockchainNetwork chain, JsonNode item, LocalDateTime timestamp) {
        String toAddress = item.path("toAddress").asText(null);
        if (toAddress == null || toAddress.isBlank()) {
            return;
        }
        WalletEntity wallet = walletRepository
                .findByAddressIgnoreCaseAndNetwork(toAddress, BlockchainNetwork.ETH)
                .orElse(null);
        if (wallet == null) {
            return;
        }

        BlockchainNetwork network = resolveNetwork(chain, item.path("rawContract").path("address").asText(null));
        if (network == null) {
            return;
        }

        BigDecimal amount = parseAmount(item);
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        String txHash = item.path("hash").asText(null);
        if (txHash == null || txHash.isBlank()
                || transactionLogService.existsByWalletIdAndTxHash(wallet.getId(), txHash)) {
            return;
        }
        transactionLogService.saveDepositSuccess(
                wallet.getUserEntity().getId(), wallet.getId(),
                network, network.getSymbol(), amount,
                item.path("fromAddress").asText(null), toAddress, txHash, timestamp);
    }

    private BlockchainNetwork resolveNetwork(BlockchainNetwork chain, String contract) {
        if (contract == null || contract.isBlank()) {
            return chain;
        }
        BlockchainProperties.Token token = blockchain.token();
        if (chain == BlockchainNetwork.ETH) {
            if (contract.equalsIgnoreCase(token.usdtErc20Contract())) return BlockchainNetwork.USDT_ERC20;
            if (contract.equalsIgnoreCase(token.usdcErc20Contract())) return BlockchainNetwork.USDC_ERC20;
        }
        if (chain == BlockchainNetwork.BNB) {
            if (contract.equalsIgnoreCase(token.usdtBep20Contract())) return BlockchainNetwork.USDT_BEP20;
            if (contract.equalsIgnoreCase(token.usdcBep20Contract())) return BlockchainNetwork.USDC_BEP20;
        }
        return null;
    }

    private BigDecimal parseAmount(JsonNode item) {
        JsonNode rawContract = item.path("rawContract");
        String rawValue = rawContract.path("rawValue").asText(null);
        JsonNode decimals = rawContract.path("decimals");
        if (rawValue != null && !rawValue.isBlank() && decimals.isNumber()) {
            try {
                BigInteger units = new BigInteger(HexUtils.stripHexPrefix(rawValue), 16);
                return new BigDecimal(units).movePointLeft(decimals.asInt());
            } catch (NumberFormatException ignored) {
            }
        }
        JsonNode value = item.path("value");
        return value.isNumber() ? value.decimalValue() : null;
    }
}