package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.util.EvmAbiEncoder;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.TokenInfo;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.example.walletbot.shared.util.HexUtils;
import lombok.extern.slf4j.Slf4j;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
public class EvmBlockchainService implements BlockchainService {

    private static final BigInteger GAS_PRICE_MULTIPLIER_NUM = BigInteger.valueOf(120);
    private static final BigInteger GAS_PRICE_MULTIPLIER_DEN = BigInteger.valueOf(100);
    private static final BigInteger DEFAULT_NATIVE_GAS_LIMIT = BigInteger.valueOf(21000);
    private static final BigInteger DEFAULT_ERC20_GAS_LIMIT = BigInteger.valueOf(65000);
    private static final BigDecimal NATIVE_FEE_FALLBACK = new BigDecimal("0.001");
    private static final BigDecimal ERC20_FEE_FALLBACK = new BigDecimal("0.003");
    private static final Pattern EVM_HEX_BODY = Pattern.compile("[0-9a-fA-F]{40}");
    private static final Pattern EVM_ZERO_BODY = Pattern.compile("0{40}");

    private final BlockchainNetwork network;
    private final Web3j web3j;
    private final boolean strictChecksum;
    private final long chainId;

    public EvmBlockchainService(BlockchainNetwork network, String rpcUrl, boolean strictChecksum, long chainId) {
        if (!network.isEvm()) {
            throw new IllegalArgumentException(network + " is not an EVM network");
        }
        this.network = network;
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        this.strictChecksum = strictChecksum;
        this.chainId = chainId;
    }

    @Override
    public BlockchainNetwork getNetwork() {
        return network;
    }

    @Override
    public WalletKeys generateWallet() {
        try {
            ECKeyPair keyPair = Keys.createEcKeyPair();
            String privateKey = keyPair.getPrivateKey().toString(16);
            String address = "0x" + Keys.getAddress(keyPair);
            return new WalletKeys(address, privateKey);
        } catch (Exception e) {
            log.error("EVM wallet generation error for {}", network, e);
            throw new BlockchainRpcException("Wallet generation error " + network.getSymbol());
        }
    }

    @Override
    public String getBalance(String address) {
        EthGetBalance response;
        try {
            response = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
        } catch (Exception e) {
            log.error("Balance retrieval error {} for {}", network, address, e);
            throw new BlockchainRpcException("Balance fetch failed on " + network);
        }
        if (response.hasError()) {
            log.error("Node error {}: {}", network, response.getError().getMessage());
            throw new BlockchainRpcException("Balance fetch failed on " + network);
        }
        BigDecimal balance = Convert.fromWei(response.getBalance().toString(), Convert.Unit.ETHER);
        return balance.setScale(6, RoundingMode.DOWN).toPlainString();
    }

    @Override
    public String send(String privateKey, String toAddress, BigDecimal amount) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            String fromAddress = credentials.getAddress();

            EthGetBalance balanceResp = web3j
                    .ethGetBalance(fromAddress, DefaultBlockParameterName.LATEST)
                    .send();

            if (balanceResp.hasError()) {
                throw new BlockchainRpcException("Failed to check balance: "
                        + balanceResp.getError().getMessage());
            }

            BigDecimal currentBalance = Convert.fromWei(
                    balanceResp.getBalance().toString(), Convert.Unit.ETHER);

            BigInteger gasPrice = computeGasPrice();
            BigDecimal gasCost = Convert.fromWei(
                    gasPrice.multiply(DEFAULT_NATIVE_GAS_LIMIT).toString(), Convert.Unit.ETHER);

            BigDecimal totalRequired = amount.add(gasCost);
            if (currentBalance.compareTo(totalRequired) < 0) {
                throw new InsufficientFundsException(
                        String.format("Insufficient funds. Balance: %s %s, required: %s (including gas ~%s)",
                                currentBalance.setScale(6, RoundingMode.HALF_UP),
                                network.getSymbol(),
                                totalRequired.setScale(6, RoundingMode.HALF_UP),
                                gasCost.setScale(6, RoundingMode.HALF_UP)));
            }

            EthGetTransactionCount countResp = web3j.ethGetTransactionCount(
                    fromAddress, DefaultBlockParameterName.PENDING).send();
            BigInteger nonce = countResp.getTransactionCount();

            BigInteger value = Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger();

            RawTransaction rawTx = RawTransaction.createEtherTransaction(
                    nonce, gasPrice, DEFAULT_NATIVE_GAS_LIMIT, toAddress, value);

            byte[] signed = TransactionEncoder.signMessage(rawTx, chainId, credentials);
            String hex = Numeric.toHexString(signed);

            EthSendTransaction txResp = web3j.ethSendRawTransaction(hex).send();

            if (txResp.hasError()) {
                String errorMsg = txResp.getError().getMessage();
                log.error("Transaction error {}: {}", network, errorMsg);

                if (errorMsg.contains("insufficient funds")) {
                    throw new InsufficientFundsException("Insufficient funds for gas");
                }
                throw new BlockchainRpcException("EVM send error: " + errorMsg);
            }

            String txHash = txResp.getTransactionHash();
            log.info("Transaction sent: {} -> {} {} tx={}",
                    fromAddress, toAddress, amount, txHash);
            return txHash;

        } catch (InsufficientFundsException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("Send error {} {} via {}", amount, network.getSymbol(), network, e);
            throw new BlockchainRpcException("EVM send failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isValidAddress(String address) {
        if (address == null || address.isBlank()) return false;
        if (!address.startsWith("0x") || address.length() != 42) return false;

        String hex = address.substring(2);
        if (!EVM_HEX_BODY.matcher(hex).matches()) return false;
        if (EVM_ZERO_BODY.matcher(hex).matches()) return false;

        boolean allLower = hex.chars().noneMatch(Character::isUpperCase);
        boolean allUpper = hex.chars().noneMatch(Character::isLowerCase);
        if (allLower) return true;
        if (allUpper) return !strictChecksum;
        return Keys.toChecksumAddress(address).equals(address);
    }

    @Override
    public String deriveAddress(String privateKey) {
        return Credentials.create(privateKey).getAddress();
    }

    @Override
    public TxStatusResult getTxStatus(String txHash, String fromAddress) {
        try {
            EthGetTransactionReceipt receiptResp = web3j.ethGetTransactionReceipt(txHash).send();
            if (receiptResp.hasError()) {
                log.warn("EVM {} receipt RPC error for {}: {}", network, txHash,
                        receiptResp.getError().getMessage());
                return TxStatusResult.pending();
            }
            Optional<TransactionReceipt> maybeReceipt = receiptResp.getTransactionReceipt();
            if (maybeReceipt.isEmpty()) {
                return TxStatusResult.pending();
            }
            TransactionReceipt receipt = maybeReceipt.get();
            BigDecimal feeNative = computeReceiptFee(receipt);
            String status = receipt.getStatus();
            boolean confirmed = "0x1".equalsIgnoreCase(status);
            if (!confirmed) {
                return TxStatusResult.reverted(feeNative);
            }
            LocalDateTime blockTs = fetchBlockTimestamp(receipt.getBlockNumber());
            return TxStatusResult.confirmed(blockTs, feeNative);
        } catch (Exception e) {
            log.warn("EVM {} getTxStatus failed for {}: {}", network, txHash, e.getMessage());
            return TxStatusResult.pending();
        }
    }

    private BigDecimal computeReceiptFee(TransactionReceipt receipt) {
        BigInteger gasUsed = receipt.getGasUsed();
        if (gasUsed == null) return null;
        BigInteger price = receipt.getEffectiveGasPrice() != null
                ? Numeric.toBigInt(receipt.getEffectiveGasPrice())
                : null;
        if (price == null) {
            try {
                price = web3j.ethGetTransactionByHash(receipt.getTransactionHash())
                        .send().getTransaction().map(t -> t.getGasPrice()).orElse(null);
            } catch (Exception e) {
                log.debug("EVM {} effective gas price lookup failed: {}", network, e.getMessage());
            }
        }
        if (price == null) return null;
        return Convert.fromWei(price.multiply(gasUsed).toString(), Convert.Unit.ETHER);
    }

    private LocalDateTime fetchBlockTimestamp(BigInteger blockNumber) {
        if (blockNumber == null) return null;
        try {
            EthBlock blockResp = web3j.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(blockNumber), false).send();
            EthBlock.Block block = blockResp.getBlock();
            if (block == null || block.getTimestamp() == null) return null;
            return LocalDateTime.ofEpochSecond(block.getTimestamp().longValueExact(), 0, ZoneOffset.UTC);
        } catch (Exception e) {
            log.debug("EVM {} block timestamp lookup failed for {}: {}", network, blockNumber, e.getMessage());
            return null;
        }
    }

    @Override
    public BigDecimal estimateSendFee() {
        return estimateGas(estimateGasLimitForNativeEthTransfer(), NATIVE_FEE_FALLBACK);
    }

    public BigDecimal estimateTokenGasCost(TokenInfo token, BlockchainNetwork tokenNetwork) {
        return estimateGas(estimateGasLimitForErc20Transfer(token, tokenNetwork), ERC20_FEE_FALLBACK);
    }

    public BigDecimal estimateTokenGasCost(String contractAddress) {
        return estimateGas(estimateGasLimitForErc20Transfer(contractAddress), ERC20_FEE_FALLBACK);
    }

    private BigInteger computeGasPrice() throws java.io.IOException {
        BigInteger raw = web3j.ethGasPrice().send().getGasPrice();
        return raw.multiply(GAS_PRICE_MULTIPLIER_NUM).divide(GAS_PRICE_MULTIPLIER_DEN);
    }

    private BigInteger estimateGasLimitForNativeEthTransfer() {
        try {
            String from = "0x0000000000000000000000000000000000000001";
            String to = "0x0000000000000000000000000000000000000002";
            Transaction tx = Transaction.createEtherTransaction(
                    from, BigInteger.ZERO, null, null, to, BigInteger.ONE);
            EthEstimateGas resp = web3j.ethEstimateGas(tx).send();
            if (!resp.hasError() && resp.getAmountUsed() != null) {
                return resp.getAmountUsed();
            }
        } catch (Exception e) {
            log.debug("ethEstimateGas native: {}", e.getMessage());
        }
        return DEFAULT_NATIVE_GAS_LIMIT;
    }

    private BigInteger estimateGasLimitForErc20Transfer(TokenInfo token, BlockchainNetwork tokenNetwork) {
        String contract = token.getContractAddress(tokenNetwork);
        if (contract == null) {
            return DEFAULT_ERC20_GAS_LIMIT;
        }
        return estimateGasLimitForErc20Transfer(contract);
    }

    private BigInteger estimateErc20GasLimit(String from, String contract, String to, BigInteger amount) {
        try {
            String data = EvmAbiEncoder.encodeTransfer(to, amount);
            Transaction tx = Transaction.createFunctionCallTransaction(
                    from, BigInteger.ZERO, null, null, contract, BigInteger.ZERO, data);
            EthEstimateGas resp = web3j.ethEstimateGas(tx).send();
            if (!resp.hasError() && resp.getAmountUsed() != null) {
                BigInteger buffered = resp.getAmountUsed()
                        .multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
                return buffered.max(DEFAULT_ERC20_GAS_LIMIT);
            }
        } catch (Exception e) {
            log.debug("ethEstimateGas erc20 (real sender): {}", e.getMessage());
        }
        return DEFAULT_ERC20_GAS_LIMIT;
    }

    private BigInteger estimateGasLimitForErc20Transfer(String contract) {
        try {
            String from = "0x0000000000000000000000000000000000000001";
            String toRecipient = "0x0000000000000000000000000000000000000002";
            String data = EvmAbiEncoder.encodeTransfer(toRecipient, BigInteger.ONE);
            Transaction tx = Transaction.createFunctionCallTransaction(
                    from, BigInteger.ZERO, null, null, contract, BigInteger.ZERO, data);
            EthEstimateGas resp = web3j.ethEstimateGas(tx).send();
            if (!resp.hasError() && resp.getAmountUsed() != null) {
                return resp.getAmountUsed();
            }
        } catch (Exception e) {
            log.debug("ethEstimateGas erc20: {}", e.getMessage());
        }
        return DEFAULT_ERC20_GAS_LIMIT;
    }

    private BigDecimal estimateGas(BigInteger gasLimit, BigDecimal fallback) {
        try {
            BigInteger gasPrice = computeGasPrice();
            return Convert.fromWei(
                    gasPrice.multiply(gasLimit).toString(), Convert.Unit.ETHER);
        } catch (Exception e) {
            log.error("Gas estimation error for {}", network, e);
            return fallback;
        }
    }

    public String getTokenBalance(String ownerAddress, TokenInfo token, BlockchainNetwork tokenNetwork) {
        String contractAddress = token.getContractAddress(tokenNetwork);
        if (contractAddress == null) {
            log.error("Contract not found for {} on {}", token.getSymbol(), tokenNetwork);
            throw new BlockchainRpcException(
                    "Token " + token.getSymbol() + " is not supported on " + tokenNetwork);
        }
        return getTokenBalance(ownerAddress, contractAddress, tokenNetwork.getDecimals());
    }

    public String getTokenBalance(String ownerAddress, String contractAddress, int decimals) {
        String value;
        try {
            String data = EvmAbiEncoder.encodeBalanceOf(ownerAddress);

            var response = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            ownerAddress, contractAddress, data),
                    DefaultBlockParameterName.LATEST).send();

            value = response.hasError() ? null : response.getValue();
        } catch (Exception e) {
            log.error("Token balance retrieval error on {} for {}: {}", network, ownerAddress, e.getMessage());
            throw new BlockchainRpcException("Token balance fetch failed on " + network);
        }
        if (value == null) {
            log.error("Token balance node error on {} for {}", network, ownerAddress);
            throw new BlockchainRpcException("Token balance fetch failed on " + network);
        }
        if (value.equals("0x") || value.equals("0x0")) {
            return "0.00";
        }
        BigInteger balance = new BigInteger(value.substring(2), 16);
        BigDecimal divisor = BigDecimal.TEN.pow(decimals);
        BigDecimal result = new BigDecimal(balance).divide(divisor, decimals, RoundingMode.HALF_UP);
        return result.stripTrailingZeros().toPlainString();
    }

    public String sendToken(String privateKey, String toAddress, BigDecimal amount,
                            TokenInfo token, BlockchainNetwork tokenNetwork) {
        String contractAddress = token.getContractAddress(tokenNetwork);
        if (contractAddress == null) {
            throw new BlockchainRpcException(
                    "Token " + token.getSymbol() + " is not supported on " + network);
        }
        BlockchainNetwork parentNetwork = tokenNetwork.getParentNetwork() != null
                ? tokenNetwork.getParentNetwork() : network;
        return sendToken(privateKey, toAddress, amount, contractAddress, tokenNetwork.getDecimals(),
                token.getSymbol(), parentNetwork, token, tokenNetwork);
    }

    private static String normalizeHex(String hex) {
        if (hex == null || hex.isBlank()) return "0x";
        return hex.startsWith("0x") || hex.startsWith("0X") ? hex : "0x" + hex;
    }

    @Override
    public String sendWithMemo(String privateKey, String toAddress, BigDecimal amount, String memo) {
        BigInteger valueWei = Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger();
        String data;
        if (memo == null || memo.isEmpty()) {
            data = "0x";
        } else {
            data = "0x" + HexUtils.bytesToHex(memo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return sendRaw(privateKey, toAddress, valueWei, data, null);
    }

    @Override
    public String sendRaw(String privateKey, String to, BigInteger valueWei,
                          String dataHex, BigInteger gasLimitOverride) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            String fromAddress = credentials.getAddress();
            String data = normalizeHex(dataHex);

            EthGetTransactionCount countResp = web3j.ethGetTransactionCount(
                    fromAddress, DefaultBlockParameterName.PENDING).send();
            BigInteger nonce = countResp.getTransactionCount();

            BigInteger gasPrice = computeGasPrice();
            BigInteger gasLimit = gasLimitOverride != null
                    ? gasLimitOverride
                    : estimateRawCallGasLimit(fromAddress, to, valueWei, data);
            BigDecimal gasCost = Convert.fromWei(
                    gasPrice.multiply(gasLimit).toString(), Convert.Unit.ETHER);
            BigDecimal nativeValue = Convert.fromWei(valueWei.toString(), Convert.Unit.ETHER);

            EthGetBalance balanceResp = web3j.ethGetBalance(
                    fromAddress, DefaultBlockParameterName.LATEST).send();
            if (balanceResp.hasError()) {
                throw new BlockchainRpcException("Failed to check balance: "
                        + balanceResp.getError().getMessage());
            }
            BigDecimal currentBalance = Convert.fromWei(
                    balanceResp.getBalance().toString(), Convert.Unit.ETHER);
            BigDecimal totalRequired = nativeValue.add(gasCost);
            if (currentBalance.compareTo(totalRequired) < 0) {
                throw new InsufficientFundsException(String.format(
                        "Insufficient funds for raw send. Balance: %s %s, required: %s (gas ~%s)",
                        currentBalance.setScale(6, RoundingMode.HALF_UP), network.getSymbol(),
                        totalRequired.setScale(6, RoundingMode.HALF_UP),
                        gasCost.setScale(6, RoundingMode.HALF_UP)));
            }

            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce, gasPrice, gasLimit, to, valueWei, data);
            byte[] signed = TransactionEncoder.signMessage(rawTx, chainId, credentials);
            EthSendTransaction resp = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
            if (resp.hasError()) {
                String msg = resp.getError().getMessage();
                if (msg != null && msg.contains("insufficient funds")) {
                    throw new InsufficientFundsException("Insufficient funds for gas");
                }
                throw new BlockchainRpcException("EVM raw send error: " + msg);
            }
            return resp.getTransactionHash();
        } catch (InsufficientFundsException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("sendRaw error {} -> {}", network, to, e);
            throw new BlockchainRpcException("EVM raw send failed: " + e.getMessage());
        }
    }

    private BigInteger estimateRawCallGasLimit(String from, String to, BigInteger value, String data) {
        try {
            Transaction tx = Transaction.createFunctionCallTransaction(
                    from, BigInteger.ZERO, null, null, to, value, data);
            EthEstimateGas resp = web3j.ethEstimateGas(tx).send();
            if (!resp.hasError() && resp.getAmountUsed() != null) {
                BigInteger used = resp.getAmountUsed();
                return used.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
            }
        } catch (Exception e) {
            log.debug("ethEstimateGas raw call: {}", e.getMessage());
        }
        return BigInteger.valueOf(500_000);
    }

    public String sendToken(String privateKey, String toAddress, BigDecimal amount,
                            String contractAddress, int decimals) {
        return sendToken(privateKey, toAddress, amount, contractAddress, decimals,
                "TOKEN", network, null, null);
    }

    private String sendToken(String privateKey, String toAddress, BigDecimal amount,
                             String contractAddress, int decimals, String tokenSymbol,
                             BlockchainNetwork parentNetwork,
                             TokenInfo token, BlockchainNetwork tokenNetwork) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            String fromAddress = credentials.getAddress();

            String tokenBalanceStr = getTokenBalance(fromAddress, contractAddress, decimals);
            BigDecimal tokenBalance;
            try {
                tokenBalance = new BigDecimal(tokenBalanceStr);
            } catch (NumberFormatException e) {
                throw new BlockchainRpcException("Failed to check token balance");
            }
            if (tokenBalance.compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                        String.format("Insufficient %s: balance %s, attempting to send %s",
                                tokenSymbol, tokenBalance, amount));
            }

            EthGetBalance nativeBalanceResp = web3j
                    .ethGetBalance(fromAddress, DefaultBlockParameterName.LATEST).send();
            BigDecimal nativeBalance = Convert.fromWei(
                    nativeBalanceResp.getBalance().toString(), Convert.Unit.ETHER);

            BigDecimal divisor = BigDecimal.TEN.pow(decimals);
            BigInteger tokenAmount = amount.multiply(divisor).toBigInteger();

            BigInteger gasPrice = computeGasPrice();
            BigInteger gasLimit = estimateErc20GasLimit(
                    fromAddress, contractAddress, toAddress, tokenAmount);
            BigDecimal gasCost = Convert.fromWei(
                    gasPrice.multiply(gasLimit).toString(), Convert.Unit.ETHER);

            if (nativeBalance.compareTo(gasCost) < 0) {
                throw new InsufficientFundsException(
                        String.format("Insufficient %s for gas. Need approximately %s %s, available %s %s",
                                parentNetwork.getSymbol(),
                                gasCost.setScale(6, RoundingMode.HALF_UP),
                                parentNetwork.getSymbol(),
                                nativeBalance.setScale(6, RoundingMode.HALF_UP),
                                parentNetwork.getSymbol()));
            }

            EthGetTransactionCount countResp = web3j.ethGetTransactionCount(
                    fromAddress, DefaultBlockParameterName.PENDING).send();
            BigInteger nonce = countResp.getTransactionCount();

            String data = EvmAbiEncoder.encodeTransfer(toAddress, tokenAmount);

            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce, gasPrice, gasLimit, contractAddress, BigInteger.ZERO, data);

            byte[] signed = TransactionEncoder.signMessage(rawTx, chainId, credentials);
            EthSendTransaction txResp = web3j.ethSendRawTransaction(
                    Numeric.toHexString(signed)).send();

            if (txResp.hasError()) {
                String msg = txResp.getError().getMessage();
                log.error("Token transaction error {}: {}", tokenSymbol, msg);
                throw new BlockchainRpcException("Token send failed: " + msg);
            }

            return txResp.getTransactionHash();

        } catch (InsufficientFundsException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("sendToken error {} {}", amount, tokenSymbol, e);
            throw new BlockchainRpcException("Token send failed: " + e.getMessage());
        }
    }
}