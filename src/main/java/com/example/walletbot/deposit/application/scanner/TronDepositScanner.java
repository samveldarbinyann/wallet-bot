package com.example.walletbot.deposit.application.scanner;

import com.example.walletbot.platform.blockchain.client.TronGridClient;
import com.example.walletbot.platform.blockchain.config.BlockchainProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.wallet.domain.WalletEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.example.walletbot.deposit.application.scanner.ScanValues.asString;
import static com.example.walletbot.deposit.application.scanner.ScanValues.unixMillisToUtc;

@Slf4j
@Service
public class TronDepositScanner {

    private static final int PAGE_LIMIT = 20;
    private static final int USDT_DECIMALS = 6;

    private final TransactionLogService transactionLogService;
    private final TronGridClient tronGrid;
    private final String usdtContract;

    public TronDepositScanner(TransactionLogService transactionLogService,
                              TronGridClient tronGrid,
                              BlockchainProperties blockchain) {
        this.transactionLogService = transactionLogService;
        this.tronGrid = tronGrid;
        this.usdtContract = blockchain.token().usdtTrc20Contract();
    }

    public boolean scan(WalletEntity wallet) {
        return scanNativeTrx(wallet) & scanTrc20(wallet);
    }

    @SuppressWarnings("unchecked")
    private boolean scanNativeTrx(WalletEntity wallet) {
        String address = wallet.getAddress();
        try {
            Map<String, Object> response = tronGrid.getAccountTransactions(address, true, PAGE_LIMIT);
            if (response == null || !response.containsKey("data"))
                return true;

            List<Map<String, Object>> txs = (List<Map<String, Object>>) response.get("data");
            Set<String> existingHashes = existingTxHashes(wallet.getId(), txs, tx -> String.valueOf(tx.get("txID")));
            for (Map<String, Object> tx : txs) {
                String txHash = String.valueOf(tx.get("txID"));
                if (existingHashes.contains(txHash))
                    continue;

                Map<String, Object> rawData = (Map<String, Object>) tx.get("raw_data");
                if (rawData == null)
                    continue;
                List<Map<String, Object>> contracts = (List<Map<String, Object>>) rawData.get("contract");
                if (contracts == null || contracts.isEmpty())
                    continue;
                Map<String, Object> contract0 = contracts.getFirst();
                if (!"TransferContract".equals(asString(contract0.get("type"))))
                    continue;
                Map<String, Object> parameter = (Map<String, Object>) contract0.get("parameter");
                if (parameter == null)
                    continue;
                Map<String, Object> value = (Map<String, Object>) parameter.get("value");
                if (value == null || value.get("amount") == null)
                    continue;

                BigDecimal amount = BigDecimal.valueOf(((Number) value.get("amount")).longValue())
                        .divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP);
                if (amount.compareTo(BigDecimal.ZERO) <= 0)
                    continue;

                transactionLogService.saveDepositSuccess(wallet.getUserEntity().getId(), wallet.getId(),
                        BlockchainNetwork.TRX, "TRX", amount, null, address, txHash,
                        unixMillisToUtc(rawData.get("timestamp")));
            }
            return true;
        } catch (Exception e) {
            log.warn("TRX scan error: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean scanTrc20(WalletEntity wallet) {
        if (usdtContract == null || usdtContract.isBlank())
            return true;
        String address = wallet.getAddress();
        try {
            Map<String, Object> response = tronGrid.getTrc20Transactions(address, true, PAGE_LIMIT);
            if (response == null || !response.containsKey("data"))
                return true;

            List<Map<String, Object>> events = (List<Map<String, Object>>) response.get("data");
            Set<String> existingHashes = existingTxHashes(wallet.getId(), events,
                    event -> asString(event.get("transaction_id")));
            for (Map<String, Object> event : events) {
                if (!"Transfer".equalsIgnoreCase(asString(event.get("type"))))
                    continue;
                if (!address.equals(asString(event.get("to"))))
                    continue;

                Map<String, Object> tokenInfo = (Map<String, Object>) event.get("token_info");
                if (tokenInfo == null || !usdtContract.equals(asString(tokenInfo.get("address"))))
                    continue;

                String txHash = asString(event.get("transaction_id"));
                if (txHash == null || txHash.isBlank() || existingHashes.contains(txHash))
                    continue;

                BigDecimal amount = parseTrc20Amount(event.get("value"), tokenInfo.get("decimals"));
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
                    continue;

                transactionLogService.saveDepositSuccess(wallet.getUserEntity().getId(), wallet.getId(),
                        BlockchainNetwork.USDT_TRC20, "USDT", amount,
                        asString(event.get("from")), address, txHash,
                        unixMillisToUtc(event.get("block_timestamp")));
            }
            return true;
        } catch (Exception e) {
            log.warn("USDT TRC20 scan error for {}: {}", address, e.getMessage());
            return false;
        }
    }

    private BigDecimal parseTrc20Amount(Object rawValue, Object decimals) {
        String raw = asString(rawValue);
        if (raw == null || raw.isBlank())
            return null;
        int scale = decimals instanceof Number n ? n.intValue() : USDT_DECIMALS;
        try {
            return new BigDecimal(raw).movePointLeft(scale).setScale(scale, RoundingMode.DOWN);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Set<String> existingTxHashes(Long walletId, List<Map<String, Object>> items,
                                         Function<Map<String, Object>, String> hashOf) {
        Set<String> hashes = new HashSet<>();
        for (Map<String, Object> item : items) {
            String h = hashOf.apply(item);
            if (h != null && !h.isBlank()) {
                hashes.add(h);
            }
        }
        return transactionLogService.findExistingTxHashes(walletId, hashes);
    }
}