package com.example.walletbot.deposit.application.scanner;

import com.example.walletbot.platform.blockchain.JettonWalletResolver;
import com.example.walletbot.platform.blockchain.chain.TonBlockchainService;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(30)
class TonDepositScannerTest {

    private static String walletAddr;
    private static String otherAddr;

    private TransactionLogService txLog;
    private TonCenterClient tonCenter;
    private JettonWalletResolver jettonWalletResolver;
    private TonDepositScanner scanner;
    private WalletEntity wallet;

    @BeforeAll
    static void generateAddress() {
        TonBlockchainService svc = new TonBlockchainService(mock(TonCenterClient.class), 698983191, "", 60L);
        walletAddr = svc.generateWallet().address();
        otherAddr = svc.generateWallet().address();
    }

    private static Map<String, Object> nativeTx(String base64Hash, String dest, String value, String source, long utime) {
        return Map.of(
                "utime", utime,
                "transaction_id", Map.of("hash", base64Hash),
                "in_msg", Map.of("destination", dest, "value", value, "source", source));
    }

    @BeforeEach
    void setUp() {
        txLog = mock(TransactionLogService.class);
        when(txLog.findExistingTxHashes(any(), any())).thenReturn(Set.of());
        tonCenter = mock(TonCenterClient.class);
        jettonWalletResolver = mock(JettonWalletResolver.class);
        scanner = new TonDepositScanner(txLog, tonCenter, jettonWalletResolver);

        UserEntity user = new UserEntity();
        user.setId(99L);
        wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setAddress(walletAddr);
        wallet.setUserEntity(user);

        when(jettonWalletResolver.resolve(walletAddr)).thenReturn(null);
    }

    @Test
    void scan_newNativeDeposit_recorded() {
        when(tonCenter.getTransactions(walletAddr, 20)).thenReturn(List.of(
                nativeTx("SEFTSDE=", walletAddr, "500000000", "EQsender", 1_700_000_000L)));

        assertThat(scanner.scan(wallet)).isTrue();

        verify(txLog).saveDepositSuccess(eq(99L), eq(10L), eq(BlockchainNetwork.TON),
                eq(BlockchainNetwork.TON.getSymbol()),
                argThat(a -> a.compareTo(new BigDecimal("0.5")) == 0),
                eq("EQsender"), eq(walletAddr), eq("4841534831"), any());
    }

    @Test
    void scan_dustBelowThreshold_skipped() {
        when(tonCenter.getTransactions(walletAddr, 20)).thenReturn(List.of(
                nativeTx("RFVTVA==", walletAddr, "500000", "EQs", 1L)));

        scanner.scan(wallet);

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scan_alreadySeenTx_skipped() {
        when(txLog.findExistingTxHashes(eq(10L), any())).thenReturn(Set.of("5345454e"));
        when(tonCenter.getTransactions(walletAddr, 20)).thenReturn(List.of(
                nativeTx("U0VFTg==", walletAddr, "500000000", "EQs", 1L)));

        scanner.scan(wallet);

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scan_differentDestination_skipped() {
        when(tonCenter.getTransactions(walletAddr, 20)).thenReturn(List.of(
                nativeTx("SA==", otherAddr, "500000000", "EQs", 1L)));

        scanner.scan(wallet);

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scan_emptyHistory_recordsNothingAndReturnsTrue() {
        when(tonCenter.getTransactions(walletAddr, 20)).thenReturn(List.of());

        assertThat(scanner.scan(wallet)).isTrue();
        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
