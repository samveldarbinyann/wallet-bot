package com.example.walletbot.platform.blockchain.token;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.chain.SolBlockchainService;
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
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.TokenResultObjects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Slf4j
public class SolTokenService implements BlockchainService {

    private static final long LAMPORTS_PER_SOL = 1_000_000_000L;
    private static final long FEE_LAMPORTS = 5000L;
    private static final long COMPUTE_UNIT_LIMIT = 200_000L;
    private static final long TOKEN_ACCOUNT_LEN = 165L;
    private static final long TOKEN_ACCOUNT_RENT_LAMPORTS = 2_039_280L;

    private final BlockchainNetwork network;
    private final SolBlockchainService solService;
    private final SolRpcClient solRpcClient;
    private final RpcClient client;
    private final PublicKey mint;
    private final int decimals;

    public SolTokenService(BlockchainNetwork network, SolBlockchainService solService,
                           SolRpcClient solRpcClient, String mintBase58, int decimals) {
        this.network = network;
        this.solService = solService;
        this.solRpcClient = solRpcClient;
        this.client = new RpcClient(solRpcClient.getRpcUrl());
        this.mint = new PublicKey(mintBase58);
        this.decimals = decimals;
    }

    @Override
    public BlockchainNetwork getNetwork() {
        return network;
    }

    @Override
    public WalletKeys generateWallet() {
        return solService.generateWallet();
    }

    @Override
    public String deriveAddress(String privateKey) {
        return solService.deriveAddress(privateKey);
    }

    @Override
    public boolean isValidAddress(String address) {
        return solService.isValidAddress(address);
    }

    @Override
    public String getFeeSymbol() {
        return BlockchainNetwork.SOL.getSymbol();
    }

    @Override
    public TxStatusResult getTxStatus(String txHash, String fromAddress) {
        return solService.getTxStatus(txHash, fromAddress);
    }

    @Override
    public String getBalance(String ownerAddress) {
        try {
            PublicKey ata = associatedTokenAddress(new PublicKey(ownerAddress));
            TokenResultObjects.TokenAmountInfo amount = client.getApi().getTokenAccountBalance(ata);
            String ui = amount.getUiAmountString();
            if (ui == null) return zero();
            return new BigDecimal(ui).setScale(decimals, RoundingMode.DOWN).toPlainString();
        } catch (RpcException e) {
            return zero();
        } catch (Exception e) {
            log.warn("{} getBalance error for {}: {}", network, ownerAddress, e.getMessage());
            return zero();
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

        PublicKey owner = signer.getPublicKey();
        PublicKey recipient;
        try {
            recipient = new PublicKey(toAddress);
        } catch (Exception e) {
            throw new BadRequestException("error.invalid_address");
        }

        PublicKey senderAta = associatedTokenAddress(owner);
        PublicKey recipientAta = associatedTokenAddress(recipient);

        BigDecimal tokenBalance = new BigDecimal(getBalance(owner.toBase58()));
        if (tokenBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient " + network.getSymbol() + ". Balance: "
                            + tokenBalance.setScale(decimals, RoundingMode.DOWN)
                            + " " + network.getSymbol() + ", required: " + amount + " " + network.getSymbol());
        }

        long tokenUnits = amount
                .movePointRight(decimals)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        long microLamports = solRpcClient.getPriorityFeeEstimate();
        long priorityFee = (microLamports * COMPUTE_UNIT_LIMIT) / 1_000_000L;

        boolean recipientAtaExists = accountExists(recipientAta);
        long rentReserve = recipientAtaExists ? 0L : rentForTokenAccount();

        long solBalance;
        try {
            solBalance = client.getApi().getBalance(owner);
        } catch (RpcException e) {
            throw new BlockchainRpcException("Failed to retrieve SOL balance: " + e.getMessage());
        }
        long requiredSol = FEE_LAMPORTS + priorityFee + rentReserve;
        if (solBalance < requiredSol) {
            throw new InsufficientFundsException(
                    "Insufficient SOL for gas. Need ~" + lamportsToSol(requiredSol)
                            + " SOL (network fee" + (rentReserve > 0 ? " + recipient account rent" : "")
                            + "), available: " + lamportsToSol(solBalance) + " SOL");
        }

        try {
            Transaction tx = new Transaction();
            tx.addInstruction(priorityFeeInstruction(microLamports));
            tx.addInstruction(AssociatedTokenProgram.createIdempotent(owner, recipient, mint));
            tx.addInstruction(TokenProgram.transferChecked(
                    senderAta, recipientAta, tokenUnits, (byte) decimals, owner, mint));

            String blockhash = solRpcClient.getLatestBlockhash();

            String txHash = client.getApi().sendTransaction(tx, List.of(signer), blockhash);

            solRpcClient.confirmTransaction(txHash, Base64.getEncoder().encodeToString(tx.serialize()));

            log.info("{} sent: {} -> {} {} {}, tx={}",
                    network, owner.toBase58(), toAddress, amount, network.getSymbol(), txHash);
            return txHash;
        } catch (InsufficientFundsException | BadRequestException | BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("{} send error: ", network, e);
            throw new BlockchainRpcException(network + " send failed: " + e.getMessage());
        }
    }

    @Override
    public BigDecimal estimateSendFee() {
        long priorityFee = (solRpcClient.getPriorityFeeEstimate() * COMPUTE_UNIT_LIMIT) / 1_000_000L;
        return lamportsToSol(FEE_LAMPORTS + priorityFee + rentForTokenAccount());
    }

    private TransactionInstruction priorityFeeInstruction(long microLamports) {
        byte[] data = new byte[9];
        data[0] = 0x03;
        long ul = microLamports;
        for (int i = 1; i <= 8; i++) {
            data[i] = (byte) (ul & 0xFF);
            ul >>= 8;
        }
        return new TransactionInstruction(
                new PublicKey("ComputeBudget111111111111111111111111111111"), List.of(), data);
    }

    private PublicKey associatedTokenAddress(PublicKey owner) {
        try {
            return PublicKey.findProgramAddress(
                    List.of(owner.toByteArray(), TokenProgram.PROGRAM_ID.toByteArray(), mint.toByteArray()),
                    AssociatedTokenProgram.PROGRAM_ID).getAddress();
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to derive associated token address: " + e.getMessage());
        }
    }

    private boolean accountExists(PublicKey account) {
        try {
            return client.getApi().getAccountInfo(account).getValue() != null;
        } catch (RpcException e) {
            return false;
        }
    }

    private long rentForTokenAccount() {
        try {
            return client.getApi().getMinimumBalanceForRentExemption(TOKEN_ACCOUNT_LEN);
        } catch (RpcException e) {
            return TOKEN_ACCOUNT_RENT_LAMPORTS;
        }
    }

    private BigDecimal lamportsToSol(long lamports) {
        return BigDecimal.valueOf(lamports)
                .divide(BigDecimal.valueOf(LAMPORTS_PER_SOL), 9, RoundingMode.HALF_UP);
    }

    private String zero() {
        return BigDecimal.ZERO.setScale(decimals, RoundingMode.DOWN).toPlainString();
    }
}
