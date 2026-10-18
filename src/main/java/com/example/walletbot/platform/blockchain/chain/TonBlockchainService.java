package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.platform.blockchain.BlockchainService;
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
import org.ton.java.smartcontract.types.WalletV4R2Config;
import org.ton.java.smartcontract.wallet.v4.WalletV4R2;
import org.ton.java.tlb.Message;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
public class TonBlockchainService implements BlockchainService {

    private static final long NANO_PER_TON = 1_000_000_000L;
    private static final BigDecimal FEE_TON = new BigDecimal("0.01");
    private static final int MNEMONIC_WORD_COUNT = 24;
    private static final byte BASECHAIN_WORKCHAIN = 0;

    private final TonCenterClient tonCenter;
    private final int walletId;
    private final String transferComment;
    private final long validUntilSeconds;

    public TonBlockchainService(TonCenterClient tonCenter, int walletId,
                                String transferComment, long validUntilSeconds) {
        this.tonCenter = tonCenter;
        this.walletId = walletId;
        this.transferComment = transferComment == null ? "" : transferComment;
        this.validUntilSeconds = validUntilSeconds;
    }

    @Override
    public BlockchainNetwork getNetwork() {
        return BlockchainNetwork.TON;
    }

    @Override
    public WalletKeys generateWallet() {
        try {
            List<String> mnemonic = Mnemonic.generate(MNEMONIC_WORD_COUNT);
            WalletV4R2 wallet = createWallet(mnemonic);
            String address = wallet.getAddress().toNonBounceable();
            String privateKey = String.join(" ", mnemonic);
            return new WalletKeys(address, privateKey);
        } catch (Exception e) {
            log.error("TON generateWallet error: ", e);
            throw new BlockchainRpcException("TON wallet generation error");
        }
    }

    @Override
    public String getBalance(String address) {
        BigDecimal balance = tonCenter.getBalanceTon(address);
        return balance.setScale(9, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public String deriveAddress(String mnemonicStr) {
        try {
            List<String> words = List.of(mnemonicStr.split(" "));
            WalletV4R2 wallet = createWallet(words);
            return wallet.getAddress().toNonBounceable();
        } catch (Exception e) {
            throw new BadRequestException("error.invalid_ton_mnemonic");
        }
    }

    @Override
    public boolean isValidAddress(String address) {
        if (address == null || address.isBlank()) return false;
        String addr = address.trim();

        try {
            Address parsed = Address.of(addr);
            if (parsed.wc != BASECHAIN_WORKCHAIN) {
                log.debug("Rejected TON address with non-basechain workchain {}: {}", parsed.wc, addr);
                return false;
            }
            if (parsed.isTestOnly) {
                log.debug("Rejected TON testnet address: {}", addr);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("Invalid TON address '{}': {}", addr, e.getMessage());
            return false;
        } catch (Error e) {
            if (e instanceof VirtualMachineError) {
                throw e;
            }
            log.debug("Invalid TON address '{}': {}", addr, e.getMessage());
            return false;
        }
    }

    @Override
    public String send(String mnemonicStr, String toAddress, BigDecimal amount) {
        return send(mnemonicStr, toAddress, amount, null);
    }

    public String sendWithBody(String mnemonicStr, String toAddress, BigDecimal amount, Cell bodyCell) {
        return sendInternal(mnemonicStr, toAddress, amount, null, bodyCell, null);
    }

    @Override
    public String sendWithMemo(String privateKey, String toAddress, BigDecimal amount, String memo) {
        return sendInternal(privateKey, toAddress, amount, null, null, memo);
    }

    public String send(String mnemonicStr, String toAddress, BigDecimal amount, Boolean bounceOverride) {
        return sendInternal(mnemonicStr, toAddress, amount, bounceOverride, null, null);
    }

    private String sendInternal(String mnemonicStr, String toAddress, BigDecimal amount,
                                Boolean bounceOverride, Cell customBody, String customComment) {
        List<String> mnemonic = List.of(mnemonicStr.split(" "));
        if (mnemonic.size() != MNEMONIC_WORD_COUNT) {
            throw new BadRequestException("error.mnemonic_word_count", MNEMONIC_WORD_COUNT);
        }

        WalletV4R2 wallet;
        try {
            wallet = createWallet(mnemonic);
        } catch (Exception e) {
            throw new BadRequestException("error.invalid_mnemonic_format");
        }

        String fromAddress = wallet.getAddress().toNonBounceable();

        BigDecimal balance = tonCenter.getBalanceTon(fromAddress);

        BigDecimal totalRequired = amount.add(FEE_TON);
        if (balance.compareTo(totalRequired) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient TON. Balance: " + balance.setScale(6, RoundingMode.HALF_UP)
                            + " TON, required: " + totalRequired.setScale(6, RoundingMode.HALF_UP)
                            + " TON (including fee ~" + FEE_TON + " TON)");
        }

        long seqno;
        try {
            seqno = resolveSeqno(fromAddress);
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to get seqno: " + e.getMessage());
        }

        try {
            long nanotons = amount.multiply(BigDecimal.valueOf(NANO_PER_TON))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();

            Address destination = Address.of(toAddress);
            boolean bounce = bounceOverride != null ? bounceOverride : destination.isBounceable;

            WalletV4R2Config.WalletV4R2ConfigBuilder configBuilder = WalletV4R2Config.builder()
                    .walletId(walletId)
                    .seqno(seqno)
                    .validUntil(System.currentTimeMillis() / 1000 + validUntilSeconds)
                    .destination(destination)
                    .amount(BigInteger.valueOf(nanotons))
                    .bounce(bounce);
            if (customBody != null) {
                configBuilder.body(customBody);
            } else if (customComment != null && !customComment.isEmpty()) {
                configBuilder.comment(customComment);
            } else if (!transferComment.isEmpty()) {
                configBuilder.comment(transferComment);
            }
            WalletV4R2Config config = configBuilder.build();

            Message externalMsg = wallet.prepareExternalMsg(config);
            if (externalMsg == null) {
                throw new BlockchainRpcException("Failed to build TON transaction");
            }
            if (seqno == 0) {
                externalMsg = Message.builder()
                        .info(externalMsg.getInfo())
                        .init(wallet.getStateInit())
                        .body(externalMsg.getBody())
                        .build();
            }
            Cell cell = externalMsg.toCell();
            if (cell == null) {
                throw new BlockchainRpcException("Failed to serialize transaction to Cell");
            }
            String result = tonCenter.sendBocReturnHash(cell.toBase64());
            log.info("TON transaction sent: {} -> {} {} TON, seqno={}, bounce={}",
                    fromAddress, toAddress, amount, seqno, bounce);
            return result;

        } catch (InsufficientFundsException | BadRequestException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("TON Send error: ", e);
            throw new BlockchainRpcException("TON send failed: " + e.getMessage());
        }
    }

    public long resolveSeqno(String address) {
        TonCenterClient.WalletInformation info = tonCenter.getWalletInformation(address);
        if (info.isUninitialized()) {
            return 0L;
        }
        if (info.seqno() == null) {
            throw new BlockchainRpcException("TonCenter returned no seqno for " + address);
        }
        return info.seqno();
    }

    @Override
    public BigDecimal estimateSendFee() {
        return FEE_TON;
    }

    @Override
    public TxStatusResult getTxStatus(String txHash, String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("TON getTxStatus called without fromAddress for {} — cannot resolve", txHash);
            return TxStatusResult.pending();
        }
        try {
            TonCenterClient.TonTxInfo info = tonCenter.findTransactionByInMessageHash(fromAddress, txHash);
            if (info == null) {
                return TxStatusResult.pending();
            }
            LocalDateTime blockTs = LocalDateTime.ofEpochSecond(info.utime(), 0, ZoneOffset.UTC);
            return TxStatusResult.confirmed(blockTs, info.feeTon());
        } catch (Exception e) {
            log.warn("TON getTxStatus failed for {}: {}", txHash, e.getMessage());
            return TxStatusResult.pending();
        }
    }

    private WalletV4R2 createWallet(List<String> mnemonic) throws Exception {
        Pair keyPair = Mnemonic.toKeyPair(mnemonic, "");
        TweetNaclFast.Signature.KeyPair tweetKeyPair =
                TweetNaclFast.Signature.keyPair_fromSeed(keyPair.getSecretKey());
        return WalletV4R2.builder().keyPair(tweetKeyPair).walletId(walletId).build();
    }
}