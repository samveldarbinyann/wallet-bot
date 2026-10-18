package com.example.walletbot.platform.blockchain.chain;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.client.SolRpcClient;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import lombok.extern.slf4j.Slf4j;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class SolBlockchainService implements BlockchainService {

    private static final long LAMPORTS_PER_SOL = 1_000_000_000L;
    private static final long FEE_LAMPORTS = 5000L;
    private static final long COMPUTE_UNIT_LIMIT = 200_000L;
    private static final long RENT_EXEMPT_MIN = 890_880L;
    private static final Pattern BASE58_ADDRESS = Pattern.compile("[1-9A-HJ-NP-Za-km-z]+");

    private final RpcClient client;
    private final SolRpcClient solRpcClient;

    public SolBlockchainService(SolRpcClient solRpcClient) {
        this.solRpcClient = solRpcClient;
        this.client = new RpcClient(solRpcClient.getRpcUrl());
    }

    @Override
    public BlockchainNetwork getNetwork() {
        return BlockchainNetwork.SOL;
    }

    @Override
    public WalletKeys generateWallet() {
        Account account = new Account();
        String address = account.getPublicKey().toBase58();
        String privateKey = java.util.Base64.getEncoder().encodeToString(account.getSecretKey());
        return new WalletKeys(address, privateKey);
    }

    @Override
    public String getBalance(String address) {
        try {
            long lamports = client.getApi().getBalance(new PublicKey(address));
            return lamportsToSol(lamports).toPlainString();
        } catch (RpcException e) {
            log.error("SOL Balance error: ", e);
            throw new BlockchainRpcException("SOL balance fetch failed");
        }
    }

    @Override
    public String deriveAddress(String privateKey) {
        return new Account(java.util.Base64.getDecoder().decode(privateKey)).getPublicKey().toBase58();
    }

    @Override
    public boolean isValidAddress(String address) {
        if (address == null || address.isBlank()) return false;

        String addr = address.trim();

        if (addr.length() < 32 || addr.length() > 44) return false;

        if (!BASE58_ADDRESS.matcher(addr).matches()) return false;

        try {
            new PublicKey(addr);
            return true;
        } catch (Exception e) {
            log.debug("Invalid SOL address '{}': {}", addr, e.getMessage());
            return false;
        }
    }

    @Override
    public String send(String privateKeyBase64, String toAddress, BigDecimal amount) {
        Account signer;
        byte[] secretKey = null;
        try {
            secretKey = java.util.Base64.getDecoder().decode(privateKeyBase64);
            signer = new Account(secretKey);
        } catch (Exception e) {
            throw new BadRequestException("error.invalid_private_key");
        } finally {
            if (secretKey != null) Arrays.fill(secretKey, (byte) 0);
        }

        String fromAddress = signer.getPublicKey().toBase58();

        long microLamports = solRpcClient.getPriorityFeeEstimate();
        long priorityFee = (microLamports * COMPUTE_UNIT_LIMIT) / 1_000_000L;

        long balanceLamports;
        try {
            balanceLamports = client.getApi().getBalance(signer.getPublicKey());
        } catch (RpcException e) {
            throw new BlockchainRpcException("Failed to retrieve balance: " + e.getMessage());
        }

        long sendLamports = amount
                .multiply(BigDecimal.valueOf(LAMPORTS_PER_SOL))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long totalRequired = sendLamports + FEE_LAMPORTS + priorityFee;

        validateBalanceAfterSend(balanceLamports, totalRequired);

        try {
            PublicKey recipient = new PublicKey(toAddress);

            TransactionInstruction instruction = SystemProgram.transfer(
                    signer.getPublicKey(), recipient, sendLamports);

            byte[] priorityData = new byte[9];
            priorityData[0] = 0x03;
            long ul = microLamports;
            for (int i = 1; i <= 8; i++) {
                priorityData[i] = (byte) (ul & 0xFF);
                ul >>= 8;
            }
            TransactionInstruction priorityIx = new TransactionInstruction(
                    new PublicKey("ComputeBudget111111111111111111111111111111"),
                    List.of(),
                    priorityData
            );

            String blockhash = solRpcClient.getLatestBlockhash();

            Transaction tx = new Transaction();
            tx.addInstruction(priorityIx);
            tx.addInstruction(instruction);

            String txHash = client.getApi().sendTransaction(tx, List.of(signer), blockhash);

            solRpcClient.confirmTransaction(txHash, Base64.getEncoder().encodeToString(tx.serialize()));

            log.info("SOL transaction sent: {} -> {} {} SOL, tx={}",
                    fromAddress, toAddress, amount, txHash);
            return txHash;

        } catch (InsufficientFundsException | BadRequestException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("SOL Send error: ", e);
            throw new BlockchainRpcException("SOL send failed: " + e.getMessage());
        }
    }

    @Override
    public BigDecimal estimateSendFee() {
        long priorityFee = (solRpcClient.getPriorityFeeEstimate() * COMPUTE_UNIT_LIMIT) / 1_000_000L;
        return lamportsToSol(FEE_LAMPORTS + priorityFee);
    }

    @Override
    public BigDecimal minRetainedBalance() {
        return lamportsToSol(RENT_EXEMPT_MIN);
    }

    @Override
    public TxStatusResult getTxStatus(String txHash, String fromAddress) {
        SolRpcClient.SignatureStatus status = solRpcClient.getSignatureStatus(txHash);
        if (status == null) {
            return TxStatusResult.pending();
        }
        if (status.isErrored()) {
            return TxStatusResult.reverted(lamportsToSol(FEE_LAMPORTS));
        }
        if (!status.isFinalized()) {
            return TxStatusResult.pending();
        }
        LocalDateTime blockTs = null;
        if (status.slot() != null) {
            Long epochSec = solRpcClient.getBlockTime(status.slot());
            if (epochSec != null) {
                blockTs = LocalDateTime.ofEpochSecond(epochSec, 0, ZoneOffset.UTC);
            }
        }
        return TxStatusResult.confirmed(blockTs, lamportsToSol(FEE_LAMPORTS));
    }

    private void validateBalanceAfterSend(long balanceLamports, long totalRequired) {
        long afterSend = balanceLamports - totalRequired;
        if (afterSend < 0) {
            BigDecimal balanceSol = lamportsToSol(balanceLamports);
            BigDecimal totalSol = lamportsToSol(totalRequired);
            throw new InsufficientFundsException(
                    "Insufficient SOL. Balance: " + balanceSol
                            + " SOL, required: " + totalSol + " SOL (including fee 0.000005 SOL)");
        }

        if (afterSend > 0 && afterSend < RENT_EXEMPT_MIN) {
            throw new InsufficientFundsException(
                    "Remaining balance after send would be " + afterSend + " lamports - below minimum " +
                            "rent exemption (" + RENT_EXEMPT_MIN + " lamports). " +
                            "Decrease the amount or send the entire balance.");
        }
    }

    private static BigDecimal lamportsToSol(long lamports) {
        return BigDecimal.valueOf(lamports)
                .divide(BigDecimal.valueOf(LAMPORTS_PER_SOL), 9, RoundingMode.HALF_UP);
    }
}