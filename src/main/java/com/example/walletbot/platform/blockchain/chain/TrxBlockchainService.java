package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.cache.TronAccountInFlightCache;
import com.example.walletbot.platform.blockchain.client.TronGridClient;
import com.example.walletbot.platform.blockchain.util.TronCryptoUtils;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.example.walletbot.shared.util.HexUtils;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class TrxBlockchainService implements BlockchainService {

    public static final long SUN_PER_TRX = 1_000_000L;
    public static final long ENERGY_PRICE_SUN = 100L;
    public static final long BANDWIDTH_PRICE_SUN = 1_000L;
    private static final BigDecimal FEE_TRX = new BigDecimal("1.5");
    private static final Pattern BASE58_ADDRESS =
            Pattern.compile("[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{34}");

    private final TronGridClient tronGrid;
    private final TronAccountInFlightCache inFlightCache;

    public TrxBlockchainService(TronGridClient tronGrid,
                                TronAccountInFlightCache inFlightCache) {
        this.tronGrid = tronGrid;
        this.inFlightCache = inFlightCache;
    }

    @Override
    public BlockchainNetwork getNetwork() {
        return BlockchainNetwork.TRX;
    }

    @Override
    public WalletKeys generateWallet() {
        SecureRandom random = new SecureRandom();
        byte[] privateKeyBytes = new byte[32];
        random.nextBytes(privateKeyBytes);
        try {
            String address = TronCryptoUtils.deriveAddressFromKeyBytes(privateKeyBytes);
            String privateKeyHex = HexUtils.bytesToHex(privateKeyBytes);
            return new WalletKeys(address, privateKeyHex);
        } catch (Exception e) {
            log.error("TRX generateWallet error: ", e);
            throw new BlockchainRpcException("TRX wallet generation error");
        } finally {
            Arrays.fill(privateKeyBytes, (byte) 0);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getBalance(String address) {
        Map<String, Object> response = getAccountData(address);
        if (response == null) {
            throw new BlockchainRpcException("TRX balance fetch failed");
        }
        try {
            if (response.containsKey("data")) {
                List<Map<String, Object>> data =
                        (List<Map<String, Object>>) response.get("data");
                if (!data.isEmpty() && data.getFirst().containsKey("balance")) {
                    long sun = ((Number) data.getFirst().get("balance")).longValue();
                    return BigDecimal.valueOf(sun)
                            .divide(BigDecimal.valueOf(SUN_PER_TRX), 6, RoundingMode.HALF_UP)
                            .toPlainString();
                }
            }
        } catch (Exception e) {
            log.error("TRX Balance error: ", e);
            throw new BlockchainRpcException("TRX balance fetch failed");
        }
        return "0.000000";
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    public AccountResources getAccountResources(String address) {
        try {
            Map<String, Object> r = tronGrid.walletGetAccountResource(address);
            if (r == null) return AccountResources.EMPTY;
            long energyLimit = asLong(r.get("EnergyLimit"));
            long energyUsed = asLong(r.get("EnergyUsed"));
            long freeNetLimit = asLong(r.get("freeNetLimit"));
            long freeNetUsed = asLong(r.get("freeNetUsed"));
            long netLimit = asLong(r.get("NetLimit"));
            long netUsed = asLong(r.get("NetUsed"));
            long energy = Math.max(0, energyLimit - energyUsed);
            long bandwidth = Math.max(0, freeNetLimit - freeNetUsed) + Math.max(0, netLimit - netUsed);
            return new AccountResources(energy, bandwidth);
        } catch (Exception e) {
            log.warn("Tron getAccountResources failed for {}: {}", address, e.getMessage());
            return AccountResources.EMPTY;
        }
    }

    public Map<String, Object> getAccountData(String address) {
        if (address == null || address.isBlank()) return null;
        try {
            return inFlightCache.dedup(address, () -> tronGrid.getV1Account(address));
        } catch (Exception e) {
            log.error("Tron getAccountData: {}", address, e);
            return null;
        }
    }

    @Override
    public boolean isValidAddress(String address) {
        if (address == null || address.isBlank()) return false;

        String addr = address.trim();

        if (!addr.startsWith("T") || addr.length() != 34) return false;
        if (!BASE58_ADDRESS.matcher(addr).matches()) return false;

        try {
            byte[] decoded = TronCryptoUtils.base58CheckDecode(addr);
            if (decoded.length != 25) return false;

            if (decoded[0] != TronCryptoUtils.TRON_MAINNET_PREFIX) {
                log.warn("Rejected non-mainnet TRX address: {}", addr);
                return false;
            }

            byte[] payload = Arrays.copyOfRange(decoded, 0, 21);
            byte[] checksum = Arrays.copyOfRange(decoded, 21, 25);
            byte[] hash = TronCryptoUtils.sha256(TronCryptoUtils.sha256(payload));
            byte[] expected = Arrays.copyOfRange(hash, 0, 4);

            return Arrays.equals(checksum, expected);
        } catch (Exception e) {
            log.debug("Invalid TRX address '{}': {}", addr, e.getMessage());
            return false;
        }
    }

    @Override
    public BigDecimal estimateSendFee() {
        return FEE_TRX;
    }

    @Override
    public String send(String privateKeyHex, String toAddress, BigDecimal amount) {
        String fromAddress;
        try {
            fromAddress = TronCryptoUtils.getAddressFromPrivateKey(privateKeyHex);
        } catch (Exception e) {
            throw new BadRequestException("error.invalid_private_key");
        }

        BigDecimal balance;
        try {
            balance = new BigDecimal(getBalance(fromAddress));
        } catch (NumberFormatException e) {
            throw new BlockchainRpcException("Failed to get wallet balance");
        }

        BigDecimal totalRequired = amount.add(FEE_TRX);
        if (balance.compareTo(totalRequired) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient TRX. Balance: " + balance.setScale(6, RoundingMode.HALF_UP)
                            + " TRX, required: " + totalRequired.setScale(6, RoundingMode.HALF_UP)
                            + " TRX (including fee ~" + FEE_TRX + " TRX)");
        }

        try {
            long sun = amount
                    .multiply(BigDecimal.valueOf(SUN_PER_TRX))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            Map<String, Object> body = new HashMap<>();
            body.put("owner_address", fromAddress);
            body.put("to_address", toAddress);
            body.put("amount", sun);
            body.put("visible", true);

            Map<String, Object> txResponse = tronGrid.walletCreateTransaction(body);

            if (txResponse == null) {
                throw new BlockchainRpcException("No response from Tron API");
            }
            if (txResponse.containsKey("Error")) {
                String error = txResponse.get("Error").toString();
                log.error("TRX createtransaction error: {}", error);
                if (error.contains("balance is not sufficient")) {
                    throw new InsufficientFundsException("Insufficient TRX according to the network");
                }
                throw new BlockchainRpcException("Error creating transaction: " + error);
            }

            String txId = txResponse.get("txID").toString();
            byte[] txIdBytes = HexUtils.hexToBytes(txId);
            byte[] signature = TronCryptoUtils.signData(txIdBytes, privateKeyHex);

            txResponse.put("signature", List.of(HexUtils.bytesToHex(signature)));

            Map<String, Object> broadcastResponse = tronGrid.walletBroadcastTransaction(txResponse);

            if (broadcastResponse != null && Boolean.TRUE.equals(broadcastResponse.get("result"))) {
                log.info("TRX transaction sent: {} -> {} {} TRX, txId={}",
                        fromAddress, toAddress, amount, txId);
                return txId;
            }

            String message = broadcastResponse != null
                    ? broadcastResponse.getOrDefault("message", "unknown").toString() : "no response";
            log.error("TRX broadcast error: {}", message);

            if (message.contains("balance is not sufficient")) {
                throw new InsufficientFundsException("Insufficient TRX according to the network");
            }
            throw new BlockchainRpcException("TRX broadcast failed: " + message);

        } catch (InsufficientFundsException | BadRequestException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("TRX Send error: ", e);
            throw new BlockchainRpcException("TRX send failed: " + e.getMessage());
        }
    }

    @Override
    public String deriveAddress(String privateKey) {
        return TronCryptoUtils.getAddressFromPrivateKey(privateKey);
    }

    @Override
    public TxStatusResult getTxStatus(String txHash, String fromAddress) {
        try {
            Map<String, Object> info = tronGrid.walletGetTransactionInfoById(txHash);
            if (info == null || info.isEmpty()) {
                return TxStatusResult.pending();
            }
            Object blockNumber = info.get("blockNumber");
            if (blockNumber == null) {
                return TxStatusResult.pending();
            }
            BigDecimal feeTrx = info.get("fee") instanceof Number n
                    ? BigDecimal.valueOf(n.longValue())
                    .divide(BigDecimal.valueOf(SUN_PER_TRX), 6, RoundingMode.HALF_UP)
                    : null;
            LocalDateTime blockTs = info.get("blockTimeStamp") instanceof Number ts
                    ? LocalDateTime.ofEpochSecond(ts.longValue() / 1000L, 0, ZoneOffset.UTC)
                    : null;
            Object receiptObj = info.get("receipt");
            String receiptResult = null;
            if (receiptObj instanceof Map<?, ?> receipt) {
                Object r = receipt.get("result");
                if (r != null) receiptResult = r.toString();
            }
            boolean receiptOk = receiptResult == null || "SUCCESS".equalsIgnoreCase(receiptResult);
            boolean topLevelFailed = info.containsKey("result")
                    && "FAILED".equalsIgnoreCase(info.get("result").toString());
            if (!receiptOk || topLevelFailed) {
                return TxStatusResult.reverted(feeTrx);
            }
            return TxStatusResult.confirmed(blockTs, feeTrx);
        } catch (Exception e) {
            log.warn("TRX getTxStatus failed for {}: {}", txHash, e.getMessage());
            return TxStatusResult.pending();
        }
    }

    public record AccountResources(long energy, long bandwidth) {
        public static final AccountResources EMPTY = new AccountResources(0, 0);
    }
}