package com.example.walletbot.deposit.application.scanner;

import com.example.walletbot.platform.blockchain.client.TronGridClient;
import com.example.walletbot.platform.blockchain.config.BlockchainProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TronDepositScannerTest {

    private static final String ADDR = "TWalletAddress";
    private static final String USDT_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

    private TronGridClient tronGrid;
    private TransactionLogService txLog;
    private TronDepositScanner scanner;
    private WalletEntity wallet;

    private static BlockchainProperties props() {
        return new BlockchainProperties(null, null,
                new BlockchainProperties.Token(null, null, null, null, USDT_CONTRACT, null, null, null), null);
    }

    @BeforeEach
    void setUp() {
        tronGrid = mock(TronGridClient.class);
        txLog = mock(TransactionLogService.class);
        when(txLog.findExistingTxHashes(any(), any())).thenReturn(Set.of());
        scanner = new TronDepositScanner(txLog, tronGrid, props());

        UserEntity user = new UserEntity();
        user.setId(3L);
        wallet = new WalletEntity();
        wallet.setId(8L);
        wallet.setAddress(ADDR);
        wallet.setUserEntity(user);
    }

    @Test
    void scan_nativeTrxTransfer_recorded() {
        when(tronGrid.getAccountTransactions(ADDR, true, 20)).thenReturn(Map.of("data", List.of(Map.of(
                "txID", "TX1",
                "raw_data", Map.of(
                        "timestamp", 1_700_000_000_000L,
                        "contract", List.of(Map.of(
                                "type", "TransferContract",
                                "parameter", Map.of("value", Map.of("amount", 1_000_000L)))))))));

        assertThat(scanner.scan(wallet)).isTrue();

        verify(txLog).saveDepositSuccess(eq(3L), eq(8L), eq(BlockchainNetwork.TRX), eq("TRX"),
                argThat(a -> a.compareTo(new BigDecimal("1")) == 0),
                isNull(), eq(ADDR), eq("TX1"), any());
    }

    @Test
    void scan_usdtTrc20Transfer_recorded() {
        when(tronGrid.getTrc20Transactions(ADDR, true, 20)).thenReturn(Map.of("data", List.of(Map.of(
                "transaction_id", "TRC20TX",
                "type", "Transfer",
                "from", "TSender",
                "to", ADDR,
                "value", "1000000",
                "block_timestamp", 1_700_000_000_000L,
                "token_info", Map.of("address", USDT_CONTRACT, "decimals", 6, "symbol", "USDT")))));

        assertThat(scanner.scan(wallet)).isTrue();

        verify(txLog).saveDepositSuccess(eq(3L), eq(8L), eq(BlockchainNetwork.USDT_TRC20), eq("USDT"),
                argThat(a -> a.compareTo(new BigDecimal("1")) == 0),
                eq("TSender"), eq(ADDR), eq("TRC20TX"), any());
    }

    @Test
    void scan_trc20DifferentContract_skipped() {
        when(tronGrid.getTrc20Transactions(ADDR, true, 20)).thenReturn(Map.of("data", List.of(Map.of(
                "transaction_id", "OTHER",
                "type", "Transfer",
                "to", ADDR,
                "value", "1000000",
                "token_info", Map.of("address", "TSomeOtherToken", "decimals", 6)))));

        scanner.scan(wallet);

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
