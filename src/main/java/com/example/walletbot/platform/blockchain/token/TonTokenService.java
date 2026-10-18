package com.example.walletbot.platform.blockchain.token;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.JettonWalletResolver;
import com.example.walletbot.platform.blockchain.chain.TonBlockchainService;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.iwebpp.crypto.TweetNaclFast;
import lombok.extern.slf4j.Slf4j;
import org.ton.java.address.Address;
import org.ton.java.cell.Cell;
import org.ton.java.mnemonic.Mnemonic;
import org.ton.java.mnemonic.Pair;
import org.ton.java.smartcontract.token.ft.JettonWallet;
import org.ton.java.smartcontract.types.WalletV4R2Config;
import org.ton.java.smartcontract.wallet.v4.WalletV4R2;
import org.ton.java.tlb.Message;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
public class TonTokenService implements BlockchainService {

    private static final BigDecimal TON_GAS = new BigDecimal("0.05");
    private static final long NANO_PER_TON = 1_000_000_000L;
    private static final int JETTON_DECIMALS = 6;
    private static final int MNEMONIC_WORD_COUNT = 24;

    private final BlockchainNetwork network;
    private final TonBlockchainService tonService;
    private final TonCenterClient tonCenter;
    private final JettonWalletResolver jettonWalletResolver;
    private final int walletId;

    public TonTokenService(BlockchainNetwork network, TonBlockchainService tonService,
                           TonCenterClient tonCenter, JettonWalletResolver jettonWalletResolver,
                           int walletId) {
        this.network = network;
        this.tonService = tonService;
        this.tonCenter = tonCenter;
        this.jettonWalletResolver = jettonWalletResolver;
        this.walletId = walletId;
    }

    @Override
    public BlockchainNetwork getNetwork() {
        return network;
    }

    @Override
    public WalletKeys generateWallet() {
        return tonService.generateWallet();
    }

    @Override
    public String deriveAddress(String privateKey) {
        return tonService.deriveAddress(privateKey);
    }

    @Override
    public boolean isValidAddress(String address) {
        return tonService.isValidAddress(address);
    }

    @Override
    public BigDecimal estimateSendFee() {
        return TON_GAS;
    }

    @Override
    public String getFeeSymbol() {
        return BlockchainNetwork.TON.getSymbol();
    }

    @Override
    public TxStatusResult getTxStatus(String txHash, String fromAddress) {
        return tonService.getTxStatus(txHash, fromAddress);
    }

    @Override
    public String getBalance(String ownerAddress) {
        try {
            String jettonWalletAddr = jettonWalletResolver.resolve(ownerAddress);
            if (jettonWalletAddr == null) return "0.000000";

            BigInteger nanoBalance = tonCenter.runGetMethodForBalance(jettonWalletAddr);
            if (nanoBalance == null) return "0.000000";

            return new BigDecimal(nanoBalance)
                    .movePointLeft(JETTON_DECIMALS)
                    .setScale(JETTON_DECIMALS, RoundingMode.DOWN)
                    .toPlainString();
        } catch (Exception e) {
            log.warn("USDT TON getBalance error for {}: {}", ownerAddress, e.getMessage());
            return "0.000000";
        }
    }

    public String sendJettonWithForwardPayload(String mnemonicStr, String toAddress,
                                               BigDecimal amount, BigDecimal forwardTonAmount,
                                               Cell forwardPayload) {
        return sendInternal(mnemonicStr, toAddress, amount, forwardTonAmount, forwardPayload);
    }

    @Override
    public String send(String mnemonicStr, String toAddress, BigDecimal amount) {
        return sendInternal(mnemonicStr, toAddress, amount, null, null);
    }

    private String sendInternal(String mnemonicStr, String toAddress, BigDecimal amount,
                                BigDecimal forwardTonAmount, Cell forwardPayload) {
        List<String> mnemonic = List.of(mnemonicStr.split(" "));
        if (mnemonic.size() != MNEMONIC_WORD_COUNT) {
            throw new BadRequestException("error.mnemonic_word_count", MNEMONIC_WORD_COUNT);
        }

        WalletV4R2 wallet;
        String fromAddress;
        try {
            Pair keyPair = Mnemonic.toKeyPair(mnemonic, "");
            TweetNaclFast.Signature.KeyPair tweetKeyPair =
                    TweetNaclFast.Signature.keyPair_fromSeed(keyPair.getSecretKey());
            wallet = WalletV4R2.builder()
                    .keyPair(tweetKeyPair)
                    .walletId(walletId)
                    .build();
            fromAddress = wallet.getAddress().toNonBounceable();
        } catch (Exception e) {
            throw new BadRequestException("error.invalid_mnemonic_format");
        }

        String senderJettonWallet = jettonWalletResolver.resolve(fromAddress);
        if (senderJettonWallet == null)
            throw new BlockchainRpcException("Failed to resolve USDT jetton wallet address");

        BigDecimal usdtBalance;
        try {
            usdtBalance = new BigDecimal(getBalance(fromAddress));
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to fetch USDT balance");
        }
        if (usdtBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient USDT. Balance: " + usdtBalance.setScale(6, RoundingMode.HALF_UP)
                            + " USDT, required: " + amount + " USDT");
        }

        BigDecimal tonBalance;
        try {
            tonBalance = new BigDecimal(tonService.getBalance(fromAddress));
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to fetch TON balance for gas");
        }
        if (tonBalance.compareTo(TON_GAS) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient TON for gas. Need ~" + TON_GAS + " TON, available: "
                            + tonBalance.setScale(4, RoundingMode.HALF_UP) + " TON");
        }

        long seqno = tonService.resolveSeqno(fromAddress);

        BigInteger jettonAmount = amount
                .movePointRight(JETTON_DECIMALS)
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger();

        BigInteger forwardAmountNano = forwardTonAmount != null
                ? forwardTonAmount.multiply(BigDecimal.valueOf(NANO_PER_TON))
                .setScale(0, RoundingMode.HALF_UP).toBigInteger()
                : BigInteger.ONE;

        Cell transferBody = JettonWallet.createTransferBody(
                0L,
                jettonAmount,
                Address.of(toAddress),
                Address.of(fromAddress),
                null,
                forwardAmountNano,
                forwardPayload
        );

        BigDecimal gasBudget = forwardTonAmount != null
                ? TON_GAS.add(forwardTonAmount)
                : TON_GAS;
        long gasNano = gasBudget
                .multiply(BigDecimal.valueOf(NANO_PER_TON))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        try {
            WalletV4R2Config config = WalletV4R2Config.builder()
                    .walletId(walletId)
                    .seqno(seqno)
                    .validUntil(System.currentTimeMillis() / 1000 + 60)
                    .destination(Address.of(senderJettonWallet))
                    .amount(BigInteger.valueOf(gasNano))
                    .bounce(true)
                    .body(transferBody)
                    .build();

            Message externalMsg = wallet.prepareExternalMsg(config);
            if (externalMsg == null)
                throw new BlockchainRpcException("Failed to build USDT TON transaction");
            if (seqno == 0) {
                externalMsg = Message.builder()
                        .info(externalMsg.getInfo())
                        .init(wallet.getStateInit())
                        .body(externalMsg.getBody())
                        .build();
            }

            Cell cell = externalMsg.toCell();
            if (cell == null)
                throw new BlockchainRpcException("Failed to serialize USDT TON transaction");

            String result = tonCenter.sendBocReturnHash(cell.toBase64());
            jettonWalletResolver.invalidate(fromAddress);
            log.info("USDT TON sent: {} -> {} {} USDT, seqno={}", fromAddress, toAddress, amount, seqno);
            return result;

        } catch (InsufficientFundsException | BadRequestException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("USDT TON send error: ", e);
            throw new BlockchainRpcException("USDT TON send failed: " + e.getMessage());
        }
    }

}