package com.example.walletbot.deposit.application.scanner;

import com.example.walletbot.platform.blockchain.config.BlockchainProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.util.UrlUtils;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.wallet.domain.WalletEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.walletbot.deposit.application.scanner.ScanValues.asString;
import static com.example.walletbot.deposit.application.scanner.ScanValues.unixSecondsToUtc;

@Slf4j
@Service
public class BtcDepositScanner {

    private final RestTemplate restTemplate;
    private final TransactionLogService transactionLogService;
    private final String mempoolBaseUrl;

    public BtcDepositScanner(RestTemplate restTemplate,
                             TransactionLogService transactionLogService,
                             BlockchainProperties blockchain) {
        this.restTemplate = restTemplate;
        this.transactionLogService = transactionLogService;
        this.mempoolBaseUrl = UrlUtils.stripTrailingSlash(blockchain.btc().mempoolBaseUrl());
    }

    public boolean scan(WalletEntity wallet) {
        if (wallet.getNetwork() != BlockchainNetwork.BTC) {
            return false;
        }
        String address = wallet.getAddress();
        String url = mempoolBaseUrl + "/address/" + address + "/txs";
        try {
            List<Map<String, Object>> txs = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    }).getBody();
            if (txs == null) {
                return true;
            }
            Set<String> existingHashes = existingTxHashes(wallet.getId(), txs);
            for (Map<String, Object> tx : txs) {
                creditIfIncoming(wallet, address, tx, existingHashes);
            }
            return true;
        } catch (HttpClientErrorException.TooManyRequests e) {
            return false;
        } catch (Exception e) {
            log.warn("BTC scan error for {}: {}", address, e.getMessage());
            return false;
        }
    }

    private Set<String> existingTxHashes(Long walletId, List<Map<String, Object>> txs) {
        Set<String> hashes = new HashSet<>();
        for (Map<String, Object> tx : txs) {
            String h = asString(tx.get("txid"));
            if (h != null && !h.isBlank()) {
                hashes.add(h);
            }
        }
        return transactionLogService.findExistingTxHashes(walletId, hashes);
    }

    @SuppressWarnings("unchecked")
    private void creditIfIncoming(WalletEntity wallet, String address, Map<String, Object> tx,
                                  Set<String> existingHashes) {
        String txHash = asString(tx.get("txid"));
        if (txHash == null || txHash.isBlank() || existingHashes.contains(txHash)) {
            return;
        }

        Map<String, Object> status = (Map<String, Object>) tx.get("status");
        if (status == null || !Boolean.TRUE.equals(status.get("confirmed"))) {
            return;
        }

        List<Map<String, Object>> vin = (List<Map<String, Object>>) tx.get("vin");
        if (vin != null) {
            for (Map<String, Object> in : vin) {
                Map<String, Object> prevout = (Map<String, Object>) in.get("prevout");
                if (prevout != null && address.equals(prevout.get("scriptpubkey_address"))) {
                    return;
                }
            }
        }

        BigDecimal satoshis = BigDecimal.ZERO;
        List<Map<String, Object>> vout = (List<Map<String, Object>>) tx.get("vout");
        if (vout != null) {
            for (Map<String, Object> out : vout) {
                if (address.equals(out.get("scriptpubkey_address")) && out.get("value") instanceof Number n) {
                    satoshis = satoshis.add(new BigDecimal(n.toString()));
                }
            }
        }
        if (satoshis.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal incoming = satoshis.movePointLeft(8).setScale(8, RoundingMode.HALF_UP);
        transactionLogService.saveDepositSuccess(
                wallet.getUserEntity().getId(), wallet.getId(),
                BlockchainNetwork.BTC, "BTC", incoming, null, address, txHash,
                unixSecondsToUtc(status.get("block_time")));
    }
}
