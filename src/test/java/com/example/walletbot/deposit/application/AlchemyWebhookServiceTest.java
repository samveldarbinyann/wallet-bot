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

class AlchemyWebhookServiceTest {

    private static final String USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
    private static final String USDC = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    private static final String USDT_BEP20 = "0x55d398326f99059fF775485246999027B3197955";
    private static final String USDC_BEP20 = "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d";
    private static final String WALLET_ADDR = "0xabc0000000000000000000000000000000000001";
    private static final String ONE_ETH_RAW = "0xde0b6b3a7640000";
    private static final String ONE_USDT_RAW = "0xf4240";

    private WalletRepository walletRepository;
    private TransactionLogService txLog;
    private AlchemyWebhookService service;

    @BeforeEach
    void setUp() {
        walletRepository = mock(WalletRepository.class);
        txLog = mock(TransactionLogService.class);
        BlockchainProperties props = new BlockchainProperties(null, null,
                new BlockchainProperties.Token(USDT, USDC, USDT_BEP20, USDC_BEP20, null, null, null, null), null);
        service = new AlchemyWebhookService(walletRepository, txLog, props);

        UserEntity user = new UserEntity();
        user.setId(11L);
        WalletEntity wallet = new WalletEntity();
        wallet.setId(22L);
        wallet.setAddress(WALLET_ADDR);
        wallet.setUserEntity(user);
        when(walletRepository.findByAddressIgnoreCaseAndNetwork(WALLET_ADDR, BlockchainNetwork.ETH))
                .thenReturn(Optional.of(wallet));
    }

    private byte[] body(String activity) {
        return ("{\"createdAt\":\"2026-06-22T10:00:00Z\",\"event\":{\"activity\":[" + activity + "]}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void process_nativeEthDeposit_credited() {
        service.process(BlockchainNetwork.ETH, body("""
                {"fromAddress":"0xsender","toAddress":"%s","hash":"0xh1","value":1.0,
                 "rawContract":{"rawValue":"%s","decimals":18}}""".formatted(WALLET_ADDR, ONE_ETH_RAW)));

        verify(txLog).saveDepositSuccess(eq(11L), eq(22L), eq(BlockchainNetwork.ETH), eq("ETH"),
                argThat(a -> a.compareTo(new BigDecimal("1")) == 0),
                eq("0xsender"), eq(WALLET_ADDR), eq("0xh1"), any());
    }

    @Test
    void process_usdtErc20Deposit_creditedAsToken() {
        service.process(BlockchainNetwork.ETH, body("""
                {"fromAddress":"0xsender","toAddress":"%s","hash":"0xh2","value":1.0,
                 "rawContract":{"address":"%s","rawValue":"%s","decimals":6}}"""
                .formatted(WALLET_ADDR, USDT, ONE_USDT_RAW)));

        verify(txLog).saveDepositSuccess(eq(11L), eq(22L), eq(BlockchainNetwork.USDT_ERC20), eq("USDT"),
                argThat(a -> a.compareTo(new BigDecimal("1")) == 0),
                eq("0xsender"), eq(WALLET_ADDR), eq("0xh2"), any());
    }

    @Test
    void process_unknownRecipient_ignored() {
        service.process(BlockchainNetwork.ETH, body("""
                {"toAddress":"0xother","hash":"0xh3","rawContract":{"rawValue":"%s","decimals":18}}"""
                .formatted(ONE_ETH_RAW)));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void process_usdtBep20Deposit_creditedAsToken() {
        service.process(BlockchainNetwork.BNB, body("""
                {"fromAddress":"0xsender","toAddress":"%s","hash":"0xh5","value":1.0,
                 "rawContract":{"address":"%s","rawValue":"%s","decimals":18}}"""
                .formatted(WALLET_ADDR, USDT_BEP20, ONE_ETH_RAW)));

        verify(txLog).saveDepositSuccess(eq(11L), eq(22L), eq(BlockchainNetwork.USDT_BEP20), eq("USDT"),
                argThat(a -> a.compareTo(new BigDecimal("1")) == 0),
                eq("0xsender"), eq(WALLET_ADDR), eq("0xh5"), any());
    }

    @Test
    void process_untrackedTokenOnBnb_ignored() {
        service.process(BlockchainNetwork.BNB, body("""
                {"toAddress":"%s","hash":"0xh4","rawContract":{"address":"%s","rawValue":"%s","decimals":6}}"""
                .formatted(WALLET_ADDR, USDT, ONE_USDT_RAW)));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void process_duplicateTx_notCredited() {
        when(txLog.existsByWalletIdAndTxHash(22L, "0xh1")).thenReturn(true);
        service.process(BlockchainNetwork.ETH, body("""
                {"toAddress":"%s","hash":"0xh1","rawContract":{"rawValue":"%s","decimals":18}}"""
                .formatted(WALLET_ADDR, ONE_ETH_RAW)));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
