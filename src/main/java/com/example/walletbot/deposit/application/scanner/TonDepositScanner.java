package com.example.walletbot.deposit.application.scanner;

import com.example.walletbot.platform.blockchain.JettonWalletResolver;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.util.HexUtils;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.wallet.domain.WalletEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ton.java.address.Address;
import org.ton.java.cell.Cell;
import org.ton.java.cell.CellBuilder;
import org.ton.java.cell.CellSlice;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.example.walletbot.deposit.application.scanner.ScanValues.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TonDepositScanner {

    private static final long JETTON_INTERNAL_TRANSFER_OP = 0x178d4519L;
    private static final int JETTON_DECIMALS = 6;
    private static final int PAGE_LIMIT = 20;

    private static final BigDecimal MIN_NATIVE_DEPOSIT_TON = new BigDecimal("0.001");

    private final TransactionLogService transactionLogService;
    private final TonCenterClient tonCenter;
    private final JettonWalletResolver jettonWalletResolver;

    public boolean scan(WalletEntity wallet) {
        return scanNative(wallet) & scanJetton(wallet);
    }

    @SuppressWarnings("unchecked")
    private static String txHash(Map<String, Object> tx) {
        Map<String, Object> txId = (Map<String, Object>) tx.get("transaction_id");
        String base64Hash = txId == null ? null : asString(txId.get("hash"));
        return base64Hash == null ? null : HexUtils.bytesToHex(Base64.getDecoder().decode(base64Hash));
    }

    @SuppressWarnings("unchecked")
    private boolean scanNative(WalletEntity wallet) {
        String address = wallet.getAddress();
        String normalizedAddress = normalizeTonAddress(address);
        try {
            List<Map<String, Object>> txs = tonCenter.getTransactions(address, PAGE_LIMIT);
            Set<String> existingHashes = existingTxHashes(wallet.getId(), txs, TonDepositScanner::txHash);
            for (Map<String, Object> tx : txs) {
                Map<String, Object> inMsg = (Map<String, Object>) tx.get("in_msg");
                if (inMsg == null)
                    continue;
                String destination = normalizeTonAddress(asString(inMsg.get("destination")));
                if (destination == null || !destination.equals(normalizedAddress))
                    continue;

                BigDecimal amount = nanoToTon(inMsg.get("value"));
                if (amount.compareTo(MIN_NATIVE_DEPOSIT_TON) < 0)
                    continue;

                String txHash = txHash(tx);
                if (txHash == null || existingHashes.contains(txHash))
                    continue;

                transactionLogService.saveDepositSuccess(wallet.getUserEntity().getId(), wallet.getId(),
                        BlockchainNetwork.TON, BlockchainNetwork.TON.getSymbol(), amount,
                        asString(inMsg.get("source")), address, txHash,
                        unixSecondsToUtc(tx.get("utime")));
            }
            return true;
        } catch (Exception e) {
            log.warn("TON scan error: {}", e.getMessage());
            return false;
        }
    }

    private BigDecimal parseJettonInternalTransferAmount(String bodyBase64) {
        try {
            Cell cell = CellBuilder.beginCell().fromBocBase64(bodyBase64).endCell();
            CellSlice slice = CellSlice.beginParse(cell);
            long op = slice.loadUint(32).longValueExact();
            if (op != JETTON_INTERNAL_TRANSFER_OP)
                return null;
            slice.loadUint(64);
            BigInteger nanoAmount = slice.loadCoins();
            return new BigDecimal(nanoAmount).movePointLeft(JETTON_DECIMALS).setScale(JETTON_DECIMALS, RoundingMode.DOWN);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean scanJetton(WalletEntity wallet) {
        String ownerAddress = wallet.getAddress();
        try {
            String jettonWalletAddr = jettonWalletResolver.resolve(ownerAddress);
            if (jettonWalletAddr == null)
                return true;

            List<Map<String, Object>> txs = tonCenter.getTransactions(jettonWalletAddr, PAGE_LIMIT);
            Set<String> existingHashes = existingTxHashes(wallet.getId(), txs, TonDepositScanner::txHash);
            for (Map<String, Object> tx : txs) {
                Map<String, Object> inMsg = (Map<String, Object>) tx.get("in_msg");
                if (inMsg == null)
                    continue;

                Map<String, Object> msgData = (Map<String, Object>) inMsg.get("msg_data");
                if (msgData == null)
                    continue;
                String bodyBase64 = asString(msgData.get("body"));
                if (bodyBase64 == null || bodyBase64.isBlank())
                    continue;

                BigDecimal usdtAmount = parseJettonInternalTransferAmount(bodyBase64);
                if (usdtAmount == null || usdtAmount.compareTo(BigDecimal.ZERO) <= 0)
                    continue;

                String txHash = txHash(tx);
                if (txHash == null || existingHashes.contains(txHash))
                    continue;

                String senderJettonWallet = asString(inMsg.get("source"));
                transactionLogService.saveDepositSuccess(
                        wallet.getUserEntity().getId(), wallet.getId(),
                        BlockchainNetwork.USDT_TON, "USDT", usdtAmount,
                        senderJettonWallet, ownerAddress, txHash,
                        unixSecondsToUtc(tx.get("utime")));
            }
            return true;
        } catch (Exception e) {
            log.warn("TON Jetton scan error for {}: {}", ownerAddress, e.getMessage());
            return false;
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

    private String normalizeTonAddress(String address) {
        if (address == null || address.isBlank())
            return null;
        try {
            return Address.of(address.trim()).toNonBounceable();
        } catch (Exception e) {
            return address.trim();
        }
    }
}