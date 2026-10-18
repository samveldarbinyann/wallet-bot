package com.example.walletbot.tools;

import com.example.walletbot.platform.blockchain.chain.EvmBlockchainService;
import com.example.walletbot.platform.blockchain.chain.TonBlockchainService;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.platform.blockchain.ratelimit.TonCenterRateLimiter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

class ReserveSweepTool {

    private static final String ETH_RECIPIENT = "0x5d17124c64acb61251b018226a8ab4a8a4389e4b";
    private static final String TON_RECIPIENT = "UQCiStJrooL8vAfMcXlHTCITmmUojHJgZvGoCAXRekuWjuAD";

    private static final String ETH_RPC_BASE = "https://eth-mainnet.g.alchemy.com/v2/";
    private static final long ETH_CHAIN_ID = 1L;
    private static final boolean ETH_STRICT_CHECKSUM = false;

    private static final String TON_RPC_URL = "https://toncenter.com/api/v2";
    private static final int TON_WALLET_ID = 698983191;
    private static final String TON_TRANSFER_COMMENT = "";
    private static final long TON_TX_VALID_UNTIL_SECONDS = 90;
    private static final int TON_RATE_LIMIT_PER_SECOND = 5;

    private static final BigDecimal ETH_GAS_SAFETY_MULTIPLIER = new BigDecimal("2");
    private static final BigDecimal TON_FEE_MARGIN = new BigDecimal("0.02");

    private static String required(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return v;
    }

    @Test
    void sweepReserveNativeBalances() throws Exception {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("RESERVE_SWEEP_TOOL")),
                "ReserveSweepTool is a manual tool; set RESERVE_SWEEP_TOOL=1 to run it.");

        boolean execute = "true".equalsIgnoreCase(System.getenv("SWEEP_EXECUTE"));

        String dbUrl = required("DB_URL");
        String dbUser = required("DB_USERNAME");
        String dbPass = required("DB_PASSWORD");
        String encPassword = required("ENCRYPTION_PASSWORD");
        String encSalt = required("ENCRYPTION_SALT");
        String alchemyKey = required("ALCHEMY_KEY");
        String tonKey = required("TON_KEY");

        TextEncryptor encryptor = Encryptors.text(encPassword, encSalt);

        System.out.println("============================================================");
        System.out.println(" RESERVE SWEEP TOOL  —  mode: " + (execute ? "EXECUTE (broadcasting!)" : "DRY RUN"));
        System.out.println("============================================================");

        String ethKey = loadReserveKey(dbUrl, dbUser, dbPass, encryptor, "ETH");
        String tonMnemonic = loadReserveKey(dbUrl, dbUser, dbPass, encryptor, "TON");

        if (ethKey == null) {
            System.out.println("\n[ETH] no reserve_wallet row — skipping.");
        } else {
            EvmBlockchainService eth = new EvmBlockchainService(
                    com.example.walletbot.shared.constant.BlockchainNetwork.ETH,
                    ETH_RPC_BASE + alchemyKey, ETH_STRICT_CHECKSUM, ETH_CHAIN_ID);

            String from = eth.deriveAddress(ethKey);
            BigDecimal balance = new BigDecimal(eth.getBalance(from));
            BigDecimal gasFee = eth.estimateSendFee();
            BigDecimal reserveForGas = gasFee.multiply(ETH_GAS_SAFETY_MULTIPLIER);
            BigDecimal amount = balance.subtract(reserveForGas).setScale(6, RoundingMode.DOWN);

            System.out.println("\n[ETH]");
            System.out.println("  from        : " + from);
            System.out.println("  to          : " + ETH_RECIPIENT);
            System.out.println("  balance     : " + balance.toPlainString() + " ETH");
            System.out.println("  est. gas fee: " + gasFee.toPlainString() + " ETH");
            System.out.println("  reserve gas : " + reserveForGas.toPlainString() + " ETH (kept for gas drift)");
            System.out.println("  send amount : " + amount.toPlainString() + " ETH");

            if (!eth.isValidAddress(ETH_RECIPIENT)) {
                System.out.println("  !! recipient address invalid — skipping ETH.");
            } else if (amount.signum() <= 0) {
                System.out.println("  -> nothing to sweep (balance does not cover gas).");
            } else if (execute) {
                String tx = eth.send(ethKey, ETH_RECIPIENT, amount);
                System.out.println("  -> BROADCAST txHash: " + tx);
            } else {
                System.out.println("  -> DRY RUN: not broadcast. Set SWEEP_EXECUTE=true to send.");
            }
        }

        if (tonMnemonic == null) {
            System.out.println("\n[TON] no reserve_wallet row — skipping.");
        } else {
            TonCenterRateLimiter limiter = new TonCenterRateLimiter(TON_RATE_LIMIT_PER_SECOND);
            TonCenterClient tonCenter = new TonCenterClient(new RestTemplate(), TON_RPC_URL, tonKey, limiter);
            TonBlockchainService ton = new TonBlockchainService(
                    tonCenter, TON_WALLET_ID, TON_TRANSFER_COMMENT, TON_TX_VALID_UNTIL_SECONDS);

            String from = ton.deriveAddress(tonMnemonic);
            BigDecimal balance = new BigDecimal(ton.getBalance(from));
            BigDecimal amount = balance.subtract(TON_FEE_MARGIN).setScale(9, RoundingMode.DOWN);

            System.out.println("\n[TON]");
            System.out.println("  from       : " + from);
            System.out.println("  to         : " + TON_RECIPIENT);
            System.out.println("  balance    : " + balance.toPlainString() + " TON");
            System.out.println("  fee margin : " + TON_FEE_MARGIN.toPlainString() + " TON (+0.01 reserved by send)");
            System.out.println("  send amount: " + amount.toPlainString() + " TON");

            if (!ton.isValidAddress(TON_RECIPIENT)) {
                System.out.println("  !! recipient address invalid — skipping TON.");
            } else if (amount.signum() <= 0) {
                System.out.println("  -> nothing to sweep (balance below fee margin).");
            } else if (execute) {
                String tx = ton.send(tonMnemonic, TON_RECIPIENT, amount);
                System.out.println("  -> BROADCAST txHash: " + tx);
            } else {
                System.out.println("  -> DRY RUN: not broadcast. Set SWEEP_EXECUTE=true to send.");
            }
        }

        System.out.println("\n============================================================");
        System.out.println(" Done" + (execute ? "." : " (dry run — nothing was sent)."));
        System.out.println("============================================================");
    }

    private String loadReserveKey(String dbUrl, String dbUser, String dbPass,
                                  TextEncryptor encryptor, String network) throws Exception {
        String sql = "SELECT encrypted_private_key FROM reserve_wallet WHERE network = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, network);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return encryptor.decrypt(rs.getString(1));
            }
        }
    }
}
