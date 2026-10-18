package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.cache.SpentUtxoCache;
import com.example.walletbot.platform.blockchain.client.GetBlockClient;
import com.example.walletbot.platform.blockchain.client.GetBlockClient.ParsedUtxo;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.springframework.web.client.RestTemplate;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class BtcBlockchainService implements BlockchainService {

    private static final long DEFAULT_FEE_PER_BYTE = 20L;
    private static final long MIN_FEE_PER_BYTE = 1L;
    private static final long MAX_FEE_PER_BYTE = 500L;
    private static final long ESTIMATED_TX_SIZE_BYTES = 141L;
    private static final long P2WPKH_INPUT_VBYTES = 68L;
    private static final long P2WPKH_OUTPUT_VBYTES = 31L;
    private static final long TX_OVERHEAD_VBYTES = 10L;

    private final NetworkParameters params;
    private final GetBlockClient getBlock;
    private final SpentUtxoCache spentUtxoCache;

    public BtcBlockchainService(RestTemplate restTemplate, String getBlockNodeUrl,
                                SpentUtxoCache spentUtxoCache,
                                NetworkParameters params, String mempoolBaseUrl) {
        this.params = params;
        this.getBlock = new GetBlockClient(restTemplate, getBlockNodeUrl, mempoolBaseUrl);
        this.spentUtxoCache = spentUtxoCache;
    }

    @Override
    public BlockchainNetwork getNetwork() {
        return BlockchainNetwork.BTC;
    }

    @Override
    public WalletKeys generateWallet() {
        ECKey key = new ECKey();
        Address address = Address.fromKey(params, key, Script.ScriptType.P2WPKH);
        return new WalletKeys(address.toString(), key.getPrivateKeyAsHex());
    }

    @Override
    public String getBalance(String address) {
        try {
            return getBlock.getBtcBalance(address)
                    .setScale(8, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            log.error("[BTC-bal] error for {}: ", address, e);
            throw new BlockchainRpcException("BTC balance fetch failed");
        }
    }

    @Override
    public String deriveAddress(String privateKey) {
        ECKey key = ECKey.fromPrivate(Numeric.hexStringToByteArray(privateKey));
        return Address.fromKey(params, key, Script.ScriptType.P2WPKH).toString();
    }

    @Override
    public boolean isValidAddress(String addressStr) {
        if (addressStr == null || addressStr.isBlank()) return false;
        String addr = addressStr.trim();
        if (addr.length() < 26 || addr.length() > 62) return false;
        try {
            Address parsed = Address.fromString(params, addr);
            return parsed.getParameters().equals(params);
        } catch (AddressFormatException e) {
            log.debug("[BTC-val] invalid address '{}': {}", addr, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("[BTC-val] unexpected error for '{}': {}", addr, e.getMessage());
            return false;
        }
    }

    @Override
    public String send(String privateKey, String toAddress, BigDecimal amount) {
        return sendInternal(privateKey, toAddress, amount, null);
    }

    @Override
    public String sendWithMemo(String privateKey, String toAddress, BigDecimal amount, String memo) {
        if (memo == null || memo.isEmpty()) {
            return sendInternal(privateKey, toAddress, amount, null);
        }
        byte[] memoBytes = memo.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (memoBytes.length > 80) {
            throw new BlockchainRpcException("BTC OP_RETURN memo exceeds 80 bytes (was " + memoBytes.length + ")");
        }
        return sendInternal(privateKey, toAddress, amount, memoBytes);
    }

    private String sendInternal(String privateKey, String toAddress, BigDecimal amount, byte[] memo) {
        ECKey key = ECKey.fromPrivate(Numeric.hexStringToByteArray(privateKey));
        Address fromAddress = Address.fromKey(params, key, Script.ScriptType.P2WPKH);
        Address recipient = Address.fromString(params, toAddress);

        List<ParsedUtxo> allUtxos = getBlock.getBtcUtxos(fromAddress.toString());
        List<ParsedUtxo> availableUtxos = allUtxos.stream()
                .filter(u -> spentUtxoCache.isAvailable(u.hash().toString(), u.index()))
                .toList();

        if (availableUtxos.isEmpty())
            throw new InsufficientFundsException("No available confirmed UTXOs (all coins are in processing)");

        long feePerByte = getDynamicFeePerByte();
        Coin value = Coin.valueOf(amount.movePointRight(8).longValue());

        try {
            Transaction tx = new Transaction(params);
            tx.addOutput(value, recipient);
            if (memo != null && memo.length > 0) {
                tx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(memo));
            }

            List<ParsedUtxo> usedUtxos = new ArrayList<>();
            Coin utxoSum = Coin.ZERO;
            Coin currentFee = Coin.valueOf(feePerByte * ESTIMATED_TX_SIZE_BYTES);

            for (ParsedUtxo utxo : availableUtxos) {
                TransactionInput input = new TransactionInput(params, tx, new byte[0],
                        new TransactionOutPoint(params, utxo.index(), utxo.hash()));
                tx.addInput(input);
                usedUtxos.add(utxo);
                utxoSum = utxoSum.add(utxo.value());

                long realSize = TX_OVERHEAD_VBYTES
                        + (long) usedUtxos.size() * P2WPKH_INPUT_VBYTES
                        + P2WPKH_OUTPUT_VBYTES * 2;
                currentFee = Coin.valueOf(feePerByte * realSize);

                if (utxoSum.isGreaterThan(value.add(currentFee))) break;
            }

            if (utxoSum.isLessThan(value.add(currentFee)))
                throw new InsufficientFundsException("Insufficient funds including fee");

            Coin change = utxoSum.subtract(value.add(currentFee));
            if (change.isGreaterThan(Coin.valueOf(600)))
                tx.addOutput(change, fromAddress);

            Script scriptCode = ScriptBuilder.createP2PKHOutputScript(key);
            for (int i = 0; i < tx.getInputs().size(); i++) {
                TransactionInput input = tx.getInputs().get(i);
                Sha256Hash sigHash = tx.hashForWitnessSignature(
                        i, scriptCode, usedUtxos.get(i).value(), Transaction.SigHash.ALL, false);
                ECKey.ECDSASignature ecSig = key.sign(sigHash);
                TransactionSignature txSig = new TransactionSignature(ecSig, Transaction.SigHash.ALL, false);
                input.setWitness(TransactionWitness.redeemP2WPKH(txSig, key));
            }

            validateTxBeforeBroadcast(tx, value, currentFee);

            String rawHex = Numeric.toHexString(tx.bitcoinSerialize());

            String txHash;
            try {
                txHash = getBlock.broadcastBtcViaMempoolSpace(rawHex);
            } catch (Exception e) {
                log.warn("[BTC-send] mempool.space broadcast failed ({}), trying GetBlock...", e.getMessage());
                txHash = getBlock.broadcastBtcViaGetBlock(rawHex);
            }

            if (txHash != null) {
                usedUtxos.forEach(u -> spentUtxoCache.markAsSpent(u.hash().toString(), u.index()));
                log.info("[BTC-send] Success! TXID: {}", txHash);
            }
            return txHash;

        } catch (InsufficientFundsException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("[BTC-send] Error: ", e);
            throw new BlockchainRpcException("BTC send failed: " + e.getMessage());
        }
    }

    @Override
    public BigDecimal estimateSendFee() {
        long feeSatoshi = getDynamicFeePerByte() * ESTIMATED_TX_SIZE_BYTES;
        return BigDecimal.valueOf(feeSatoshi).movePointLeft(8).setScale(8, RoundingMode.HALF_UP);
    }

    @Override
    public TxStatusResult getTxStatus(String txHash, String fromAddress) {
        GetBlockClient.BtcTxStatus status = getBlock.getBtcTxStatus(txHash);
        if (status == null) {
            return TxStatusResult.pending();
        }
        if (!status.confirmed()) {
            return TxStatusResult.pending();
        }
        LocalDateTime blockTs = status.blockTime() != null
                ? LocalDateTime.ofEpochSecond(status.blockTime(), 0, ZoneOffset.UTC)
                : null;
        BigDecimal feeBtc = status.feeSatoshi() != null
                ? BigDecimal.valueOf(status.feeSatoshi()).movePointLeft(8).setScale(8, RoundingMode.HALF_UP)
                : null;
        return TxStatusResult.confirmed(blockTs, feeBtc);
    }

    private long getDynamicFeePerByte() {
        try {
            Map<String, Object> fees = getBlock.getRecommendedFeePerByte();
            if (fees != null && fees.containsKey("halfHourFee")) {
                long fee = ((Number) fees.get("halfHourFee")).longValue();
                return Math.clamp(fee, MIN_FEE_PER_BYTE, MAX_FEE_PER_BYTE);
            }
        } catch (Exception e) {
            log.warn("[BTC-fee] mempool.space unavailable, using fallback {} sat/byte: {}",
                    DEFAULT_FEE_PER_BYTE, e.getMessage());
        }
        return DEFAULT_FEE_PER_BYTE;
    }

    private void validateTxBeforeBroadcast(Transaction tx, Coin sendValue, Coin fee) {
        if (tx.getInputs().isEmpty())
            throw new IllegalStateException("Transaction has no inputs");
        if (tx.getOutputs().isEmpty())
            throw new IllegalStateException("Transaction has no outputs");
        if (fee.isGreaterThan(sendValue))
            throw new IllegalStateException(
                    "Fee (" + fee.toFriendlyString() + ") is greater than send amount ("
                            + sendValue.toFriendlyString() + ")");
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionWitness witness = tx.getInputs().get(i).getWitness();
            if (witness == null || witness.getPushCount() == 0)
                throw new IllegalStateException("Input " + i + " has no witness data");
        }
    }
}