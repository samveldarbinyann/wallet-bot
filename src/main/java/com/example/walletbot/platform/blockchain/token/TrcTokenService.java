package com.example.walletbot.platform.blockchain.token;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.chain.TrxBlockchainService;
import com.example.walletbot.platform.blockchain.chain.TrxBlockchainService.AccountResources;
import com.example.walletbot.platform.blockchain.client.TronGridClient;
import com.example.walletbot.platform.blockchain.util.EvmAbiEncoder;
import com.example.walletbot.platform.blockchain.util.TronCryptoUtils;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.example.walletbot.shared.util.HexUtils;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TrcTokenService implements BlockchainService {

    private static final int USDT_DECIMALS = 6;
    private static final BigDecimal TRX_FEE = new BigDecimal("14");
    private static final long ENERGY_EXISTING_RECIPIENT = 65_000L;
    private static final long ENERGY_FRESH_RECIPIENT = 130_000L;
    private static final long BANDWIDTH_PER_TX = 345L;
    private static final BigDecimal ENERGY_MARGIN = new BigDecimal("1.10");
    private static final long FEE_LIMIT_SUN = 20_000_000L;

    private final BlockchainNetwork network;
    private final TrxBlockchainService trxService;
    private final TronGridClient tronGrid;
    private final String usdtContract;

    public TrcTokenService(BlockchainNetwork network, TrxBlockchainService trxService,
                           TronGridClient tronGrid, String usdtContract) {
        this.network = network;
        this.trxService = trxService;
        this.tronGrid = tronGrid;
        this.usdtContract = usdtContract;
    }

    @Override
    public BlockchainNetwork getNetwork() {
        return network;
    }

    @Override
    public WalletKeys generateWallet() {
        return trxService.generateWallet();
    }

    @Override
    public boolean isValidAddress(String address) {
        return trxService.isValidAddress(address);
    }

    @Override
    public BigDecimal estimateSendFee() {
        return TRX_FEE;
    }

    @Override
    public BigDecimal estimateNativeBurn(String fromAddress, String toAddress, BigDecimal amount) {
        AccountResources res = (fromAddress == null || fromAddress.isBlank())
                ? AccountResources.EMPTY
                : trxService.getAccountResources(fromAddress);
        long energyNeeded = estimateTransferEnergy(fromAddress, toAddress, amount);
        long energyShortfall = Math.max(0, energyNeeded - res.energy());
        long bandwidthShortfall = Math.max(0, BANDWIDTH_PER_TX - res.bandwidth());
        long sun = energyShortfall * TrxBlockchainService.ENERGY_PRICE_SUN
                + bandwidthShortfall * TrxBlockchainService.BANDWIDTH_PRICE_SUN;
        return BigDecimal.valueOf(sun)
                .divide(BigDecimal.valueOf(TrxBlockchainService.SUN_PER_TRX), 6, RoundingMode.UP);
    }

    public long estimateTransferEnergy(String fromAddress, String toAddress, BigDecimal amount) {
        if (toAddress == null || toAddress.isBlank() || fromAddress == null || fromAddress.isBlank()) {
            return ENERGY_FRESH_RECIPIENT;
        }
        try {
            Map<String, Object> body = transferCallParams(fromAddress, toAddress, amount);
            Map<String, Object> resp = tronGrid.walletTriggerConstantContract(body);
            if (resp != null && resp.get("energy_used") instanceof Number n && n.longValue() > 0) {
                return BigDecimal.valueOf(n.longValue()).multiply(ENERGY_MARGIN)
                        .setScale(0, RoundingMode.UP).longValueExact();
            }
        } catch (Exception e) {
            log.debug("triggerconstantcontract energy estimate failed for {}->{}: {}",
                    fromAddress, toAddress, e.getMessage());
        }
        return ENERGY_FRESH_RECIPIENT;
    }

    @Override
    public String getFeeSymbol() {
        return BlockchainNetwork.TRX.getSymbol();
    }

    @Override
    public String deriveAddress(String privateKey) {
        return trxService.deriveAddress(privateKey);
    }

    @Override
    public TxStatusResult getTxStatus(String txHash, String fromAddress) {
        return trxService.getTxStatus(txHash, fromAddress);
    }

    @Override
    public String getBalance(String address) {
        Map<String, Object> accountInfo = trxService.getAccountData(address);
        if (accountInfo == null) {
            throw new BlockchainRpcException("USDT TRC20 balance fetch failed");
        }
        try {
            return parseUsdtBalance(accountInfo).toPlainString();
        } catch (Exception e) {
            log.error("USDT TRC20 getBalance error for {}: ", address, e);
            throw new BlockchainRpcException("USDT TRC20 balance fetch failed");
        }
    }

    @Override
    public String send(String privateKeyHex, String toAddress, BigDecimal amount) {
        String fromAddress;
        try {
            fromAddress = TronCryptoUtils.getAddressFromPrivateKey(privateKeyHex);
        } catch (Exception e) {
            throw new BadRequestException("error.invalid_private_key");
        }

        Map<String, Object> accountInfo = trxService.getAccountData(fromAddress);
        if (accountInfo == null) {
            throw new BlockchainRpcException("Failed to retrieve account data");
        }

        BigDecimal usdtBalance = parseUsdtBalance(accountInfo);
        if (usdtBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient USDT. Balance: " + usdtBalance.setScale(6, RoundingMode.HALF_UP)
                            + " USDT, required: " + amount + " USDT");
        }

        BigDecimal trxBalance = parseTrxBalance(accountInfo);
        BigDecimal requiredTrx = estimateNativeBurn(fromAddress, toAddress, amount);
        if (trxBalance.compareTo(requiredTrx) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient TRX for fee. Required ~" + requiredTrx.setScale(6, RoundingMode.HALF_UP)
                            + " TRX, available: " + trxBalance.setScale(6, RoundingMode.HALF_UP) + " TRX");
        }

        try {
            Map<String, Object> body = new HashMap<>(transferCallParams(fromAddress, toAddress, amount));
            body.put("fee_limit", FEE_LIMIT_SUN);
            body.put("call_value", 0);

            Map<String, Object> txResponse = tronGrid.walletTriggerSmartContract(body);

            if (txResponse == null || !txResponse.containsKey("transaction")) {
                String err = txResponse != null
                        ? txResponse.getOrDefault("Error", "unknown").toString() : "no response";
                throw new BlockchainRpcException("Transaction creation failed: " + err);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> tx = (Map<String, Object>) txResponse.get("transaction");
            String txId = tx.get("txID").toString();
            byte[] signature = TronCryptoUtils.signData(HexUtils.hexToBytes(txId), privateKeyHex);
            tx.put("signature", List.of(HexUtils.bytesToHex(signature)));

            Map<String, Object> broadcastResp = tronGrid.walletBroadcastTransaction(tx);

            if (broadcastResp != null && Boolean.TRUE.equals(broadcastResp.get("result"))) {
                log.info("USDT TRC20 sent: {} -> {} {} USDT, txId={}",
                        fromAddress, toAddress, amount, txId);
                return txId;
            }

            String message = broadcastResp != null
                    ? broadcastResp.getOrDefault("message", "unknown").toString() : "no response";
            log.error("USDT TRC20 broadcast error: {}", message);
            throw new BlockchainRpcException("USDT TRC20 broadcast failed: " + message);

        } catch (InsufficientFundsException | BadRequestException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("USDT TRC20 send error: ", e);
            throw new BlockchainRpcException("USDT TRC20 send failed: " + e.getMessage());
        }
    }

    private Map<String, Object> transferCallParams(String fromAddress, String toAddress, BigDecimal amount) {
        long tokenAmount = amount
                .movePointRight(USDT_DECIMALS)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        String toHex = EvmAbiEncoder.leftPad64(TronCryptoUtils.tronAddressToHex(toAddress));
        String amtHex = EvmAbiEncoder.leftPad64(Long.toHexString(tokenAmount));
        Map<String, Object> body = new HashMap<>();
        body.put("owner_address", fromAddress);
        body.put("contract_address", usdtContract);
        body.put("function_selector", "transfer(address,uint256)");
        body.put("parameter", toHex + amtHex);
        body.put("visible", true);
        return body;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal parseUsdtBalance(Map<String, Object> accountInfo) {
        try {
            if (accountInfo.containsKey("data")) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) accountInfo.get("data");
                if (!data.isEmpty() && data.getFirst().containsKey("trc20")) {
                    List<Map<String, String>> trc20List =
                            (List<Map<String, String>>) data.getFirst().get("trc20");
                    for (Map<String, String> token : trc20List) {
                        if (token.containsKey(usdtContract)) {
                            return new BigDecimal(token.get(usdtContract))
                                    .movePointLeft(USDT_DECIMALS)
                                    .setScale(6, RoundingMode.HALF_UP);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("parseUsdtBalance error: ", e);
        }
        return BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal parseTrxBalance(Map<String, Object> accountInfo) {
        try {
            if (accountInfo.containsKey("data")) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) accountInfo.get("data");
                if (!data.isEmpty() && data.getFirst().containsKey("balance")) {
                    long sun = ((Number) data.getFirst().get("balance")).longValue();
                    return BigDecimal.valueOf(sun)
                            .divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception e) {
            log.error("parseTrxBalance error: ", e);
        }
        return BigDecimal.ZERO;
    }
}