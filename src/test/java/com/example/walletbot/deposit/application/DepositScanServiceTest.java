package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.application.scanner.BtcDepositScanner;
import com.example.walletbot.deposit.application.scanner.TonDepositScanner;
import com.example.walletbot.deposit.application.scanner.TronDepositScanner;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositScanServiceTest {

    @Mock
    WalletRepository walletRepository;
    @Mock
    BtcDepositScanner btcDepositScanner;
    @Mock
    TronDepositScanner tronDepositScanner;
    @Mock
    TonDepositScanner tonDepositScanner;

    @InjectMocks
    DepositScanService depositScanService;

    private WalletEntity wallet(BlockchainNetwork network) {
        UserEntity user = new UserEntity();
        user.setId(7L);
        WalletEntity wallet = new WalletEntity();
        wallet.setId(42L);
        wallet.setUserEntity(user);
        wallet.setNetwork(network);
        wallet.setAddress("addr");
        return wallet;
    }

    @Test
    void scanSingleWallet_routesBtcToBtcScanner() {
        depositScanService.scanSingleWallet(wallet(BlockchainNetwork.BTC));

        verify(btcDepositScanner).scan(any());
        verifyNoInteractions(tronDepositScanner);
    }

    @Test
    void scanSingleWallet_routesTrxToTronScanner() {
        depositScanService.scanSingleWallet(wallet(BlockchainNetwork.TRX));

        verify(tronDepositScanner).scan(any());
        verifyNoInteractions(btcDepositScanner, tonDepositScanner);
    }

    @Test
    void scanSingleWallet_routesTonToTonScanner() {
        depositScanService.scanSingleWallet(wallet(BlockchainNetwork.TON));

        verify(tonDepositScanner).scan(any());
        verifyNoInteractions(btcDepositScanner, tronDepositScanner);
    }

    @Test
    void scanSingleWallet_ignoresWebhookOnlyChains() {
        for (BlockchainNetwork network : List.of(BlockchainNetwork.ETH, BlockchainNetwork.SOL)) {
            depositScanService.scanSingleWallet(wallet(network));
        }

        verifyNoInteractions(btcDepositScanner, tronDepositScanner, tonDepositScanner);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void scanSingleWallet_ignoresUnsupportedNetwork() {
        depositScanService.scanSingleWallet(wallet(BlockchainNetwork.USDT_ERC20));

        verifyNoInteractions(btcDepositScanner, tronDepositScanner);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void scanSingleWallet_skipsRecentlyScannedWallet() {
        WalletEntity wallet = wallet(BlockchainNetwork.TRX);
        wallet.setLastDepositScanAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        depositScanService.scanSingleWallet(wallet);

        verifyNoInteractions(btcDepositScanner, tronDepositScanner);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void scanSingleWallet_slowChainDebounceOutlastsFastChain() {
        WalletEntity btc = wallet(BlockchainNetwork.BTC);
        btc.setLastDepositScanAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));

        depositScanService.scanSingleWallet(btc);

        verifyNoInteractions(btcDepositScanner);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void scanSingleWallet_updatesTimestampWhenScanSucceeds() {
        WalletEntity wallet = wallet(BlockchainNetwork.TRX);
        when(tronDepositScanner.scan(wallet)).thenReturn(true);

        depositScanService.scanSingleWallet(wallet);

        assertThat(wallet.getLastDepositScanAt()).isNotNull();
        verify(walletRepository).save(wallet);
    }

    @Test
    void scanSingleWallet_leavesTimestampWhenScanFails() {
        WalletEntity wallet = wallet(BlockchainNetwork.TRX);
        when(tronDepositScanner.scan(wallet)).thenReturn(false);

        depositScanService.scanSingleWallet(wallet);

        assertThat(wallet.getLastDepositScanAt()).isNull();
        verify(walletRepository, never()).save(any());
    }

    @Test
    void scanUserWallets_continuesAfterScannerThrows() {
        List<WalletEntity> wallets = List.of(wallet(BlockchainNetwork.TRX), wallet(BlockchainNetwork.TRX));
        when(walletRepository.findByUserEntityId(7L)).thenReturn(wallets);
        when(tronDepositScanner.scan(any())).thenThrow(new RuntimeException("rpc down"));

        assertThatCode(() -> depositScanService.scanUserWallets(7L)).doesNotThrowAnyException();

        verify(tronDepositScanner, times(2)).scan(any());
    }

    @Test
    void scanUserWallets_doesNothingForNullUserId() {
        depositScanService.scanUserWallets(null);

        verifyNoInteractions(walletRepository, btcDepositScanner, tronDepositScanner);
    }

    @Test
    void scanUserWallets_doesNothingWhenUserHasNoWallets() {
        when(walletRepository.findByUserEntityId(7L)).thenReturn(List.of());

        depositScanService.scanUserWallets(7L);

        verifyNoInteractions(btcDepositScanner, tronDepositScanner);
    }

    @Test
    void scanUserWalletsAsync_delegatesToScan() {
        depositScanService.setIoExecutor(Runnable::run);
        when(walletRepository.findByUserEntityId(7L)).thenReturn(List.of(wallet(BlockchainNetwork.TRX)));

        depositScanService.scanUserWalletsAsync(7L);

        verify(tronDepositScanner).scan(any());
    }
}
