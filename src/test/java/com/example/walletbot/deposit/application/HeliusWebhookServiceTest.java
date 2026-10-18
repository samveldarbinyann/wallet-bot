package com.example.walletbot.deposit.application;

import com.example.walletbot.platform.blockchain.config.BlockchainProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeliusWebhookServiceTest {

    private static final String USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB";
    private static final String USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
    private static final String WALLET_ADDR = "So1WalletAddr11111111111111111111111111111";

    private WalletRepository walletRepository;
    private TransactionLogService txLog;
    private HeliusWebhookService service;

    @BeforeEach
    void setUp() {
        walletRepository = mock(WalletRepository.class);
        txLog = mock(TransactionLogService.class);
        BlockchainProperties props = new BlockchainProperties(null, null,
                new BlockchainProperties.Token(null, null, null, null, null, null, USDT_MINT, USDC_MINT), null);
        service = new HeliusWebhookService(walletRepository, txLog, props);

        UserEntity user = new UserEntity();
        user.setId(11L);
        WalletEntity wallet = new WalletEntity();
        wallet.setId(22L);
        wallet.setAddress(WALLET_ADDR);
        wallet.setUserEntity(user);
        when(walletRepository.findByAddressIgnoreCaseAndNetwork(WALLET_ADDR, BlockchainNetwork.SOL))
                .thenReturn(Optional.of(wallet));
    }

    private byte[] body(String tx) {
        return ("[" + tx + "]").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void process_nativeSolDeposit_credited() {
        service.process(body("""
                {"signature":"SIG1","timestamp":1700000000,"transactionError":null,
                 "nativeTransfers":[{"fromUserAccount":"sender","toUserAccount":"%s","amount":2000000000}]}"""
                .formatted(WALLET_ADDR)));

        verify(txLog).saveDepositSuccess(eq(11L), eq(22L), eq(BlockchainNetwork.SOL), eq("SOL"),
                argThat(a -> a.compareTo(new BigDecimal("2")) == 0),
                eq("sender"), eq(WALLET_ADDR), eq("SIG1"), any());
    }

    @Test
    void process_usdtSplDeposit_creditedAsToken() {
        service.process(body("""
                {"signature":"SIG2","timestamp":1700000000,
                 "tokenTransfers":[{"fromUserAccount":"sender","toUserAccount":"%s","mint":"%s","tokenAmount":12.5}]}"""
                .formatted(WALLET_ADDR, USDT_MINT)));

        verify(txLog).saveDepositSuccess(eq(11L), eq(22L), eq(BlockchainNetwork.USDT_SOL), eq("USDT"),
                argThat(a -> a.compareTo(new BigDecimal("12.5")) == 0),
                eq("sender"), eq(WALLET_ADDR), eq("SIG2"), any());
    }

    @Test
    void process_usdcSplDeposit_creditedAsToken() {
        service.process(body("""
                {"signature":"SIG7","tokenTransfers":[{"toUserAccount":"%s","mint":"%s","tokenAmount":3}]}"""
                .formatted(WALLET_ADDR, USDC_MINT)));

        verify(txLog).saveDepositSuccess(eq(11L), eq(22L), eq(BlockchainNetwork.USDC_SOL), eq("USDC"),
                argThat(a -> a.compareTo(new BigDecimal("3")) == 0),
                any(), eq(WALLET_ADDR), eq("SIG7"), any());
    }

    @Test
    void process_unknownRecipient_ignored() {
        service.process(body("""
                {"signature":"SIG3","nativeTransfers":[{"toUserAccount":"other","amount":1000000000}]}"""));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void process_untrackedMint_ignored() {
        service.process(body("""
                {"signature":"SIG4","tokenTransfers":[{"toUserAccount":"%s","mint":"OtherMint","tokenAmount":5}]}"""
                .formatted(WALLET_ADDR)));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void process_outgoingTransfer_ignored() {
        service.process(body("""
                {"signature":"SIG5","nativeTransfers":[
                 {"fromUserAccount":"%s","toUserAccount":"other","amount":1000000000}]}"""
                .formatted(WALLET_ADDR)));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void process_failedTransaction_skipped() {
        service.process(body("""
                {"signature":"SIG6","transactionError":{"InstructionError":[0,"Custom"]},
                 "nativeTransfers":[{"toUserAccount":"%s","amount":1000000000}]}"""
                .formatted(WALLET_ADDR)));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void process_duplicateTx_notCredited() {
        when(txLog.existsByWalletIdAndTxHash(22L, "SIG1")).thenReturn(true);
        service.process(body("""
                {"signature":"SIG1","nativeTransfers":[{"toUserAccount":"%s","amount":2000000000}]}"""
                .formatted(WALLET_ADDR)));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
