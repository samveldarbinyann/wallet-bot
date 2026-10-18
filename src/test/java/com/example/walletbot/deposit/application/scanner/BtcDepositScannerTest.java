package com.example.walletbot.deposit.application.scanner;

import com.example.walletbot.platform.blockchain.config.BlockchainProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Timeout(15)
class BtcDepositScannerTest {

    private static final String ADDR = "btcWalletAddr";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private TransactionLogService txLog;
    private BtcDepositScanner scanner;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        txLog = mock(TransactionLogService.class);
        when(txLog.findExistingTxHashes(any(), any())).thenReturn(Set.of());
        BlockchainProperties props = new BlockchainProperties(
                new BlockchainProperties.Btc("mainnet", "http://mempool"), null, null, null);
        scanner = new BtcDepositScanner(restTemplate, txLog, props);
    }

    private WalletEntity wallet(BlockchainNetwork network) {
        UserEntity user = new UserEntity();
        user.setId(7L);
        WalletEntity w = new WalletEntity();
        w.setId(3L);
        w.setAddress(ADDR);
        w.setNetwork(network);
        w.setUserEntity(user);
        return w;
    }

    private void expectTxsRespondWith(String json) {
        server.expect(requestTo("http://mempool/address/" + ADDR + "/txs"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void scan_confirmedDeposit_sumsSatoshiOutputsToWalletAndConvertsToBtc() {
        expectTxsRespondWith("""
                [{"txid":"TX1","status":{"confirmed":true,"block_time":1700000000},
                  "vin":[{"prevout":{"scriptpubkey_address":"someoneElse"}}],
                  "vout":[
                    {"scriptpubkey_address":"%s","value":30000000},
                    {"scriptpubkey_address":"other","value":100000000},
                    {"scriptpubkey_address":"%s","value":20000000}
                  ]}]""".formatted(ADDR, ADDR));

        assertThat(scanner.scan(wallet(BlockchainNetwork.BTC))).isTrue();

        verify(txLog).saveDepositSuccess(eq(7L), eq(3L), eq(BlockchainNetwork.BTC), eq("BTC"),
                argThat(a -> a.compareTo(new BigDecimal("0.5")) == 0),
                isNull(), eq(ADDR), eq("TX1"), any());
        server.verify();
    }

    @Test
    void scan_smallSatoshiAmount_isNotInflatedToWholeCoins() {
        expectTxsRespondWith("""
                [{"txid":"TX2","status":{"confirmed":true,"block_time":1700000000},
                  "vout":[{"scriptpubkey_address":"%s","value":13091}]}]""".formatted(ADDR));

        assertThat(scanner.scan(wallet(BlockchainNetwork.BTC))).isTrue();

        verify(txLog).saveDepositSuccess(eq(7L), eq(3L), eq(BlockchainNetwork.BTC), eq("BTC"),
                argThat(a -> a.compareTo(new BigDecimal("0.00013091")) == 0),
                isNull(), eq(ADDR), eq("TX2"), any());
    }

    @Test
    void scan_unconfirmedTx_notRecorded() {
        expectTxsRespondWith("""
                [{"txid":"TX3","status":{"confirmed":false},
                  "vout":[{"scriptpubkey_address":"%s","value":50000000}]}]""".formatted(ADDR));

        assertThat(scanner.scan(wallet(BlockchainNetwork.BTC))).isTrue();

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scan_ownSpendWithChangeBackToWallet_notCreditedAsDeposit() {
        expectTxsRespondWith("""
                [{"txid":"TX4","status":{"confirmed":true,"block_time":1700000000},
                  "vin":[{"prevout":{"scriptpubkey_address":"%s"}}],
                  "vout":[{"scriptpubkey_address":"%s","value":40000000}]}]""".formatted(ADDR, ADDR));

        assertThat(scanner.scan(wallet(BlockchainNetwork.BTC))).isTrue();

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scan_alreadySeenTx_skipped() {
        when(txLog.findExistingTxHashes(eq(3L), any())).thenReturn(Set.of("TX1"));
        expectTxsRespondWith("""
                [{"txid":"TX1","status":{"confirmed":true,"block_time":1},
                  "vout":[{"scriptpubkey_address":"%s","value":30000000}]}]""".formatted(ADDR));

        scanner.scan(wallet(BlockchainNetwork.BTC));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scan_noOutputsToWallet_skipped() {
        expectTxsRespondWith("""
                [{"txid":"TX1","status":{"confirmed":true,"block_time":1},
                  "vout":[{"scriptpubkey_address":"other","value":50000000}]}]""");

        scanner.scan(wallet(BlockchainNetwork.BTC));

        verify(txLog, never()).saveDepositSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scan_rateLimited_returnsFalse() {
        server.expect(requestTo("http://mempool/address/" + ADDR + "/txs"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(scanner.scan(wallet(BlockchainNetwork.BTC))).isFalse();
    }

    @Test
    void scan_unsupportedNetwork_returnsFalseNoHttp() {
        assertThat(scanner.scan(wallet(BlockchainNetwork.ETH))).isFalse();
        server.verify();
    }
}
