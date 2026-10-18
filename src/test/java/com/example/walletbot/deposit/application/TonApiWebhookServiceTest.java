package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.application.scanner.TonDepositScanner;
import com.example.walletbot.platform.blockchain.chain.TonBlockchainService;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.util.AddressNormalizer;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ton.java.address.Address;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.Mockito.*;

class TonApiWebhookServiceTest {

    private static String friendlyAddr;
    private static String rawAddr;

    private WalletRepository walletRepository;
    private TonDepositScanner scanner;
    private WalletEntity wallet;
    private TonApiWebhookService service;

    @BeforeAll
    static void generateAddress() {
        TonBlockchainService svc = new TonBlockchainService(mock(TonCenterClient.class), 698983191, "", 60L);
        friendlyAddr = svc.generateWallet().address();
        rawAddr = Address.of(friendlyAddr).toRaw();
    }

    @BeforeEach
    void setUp() {
        walletRepository = mock(WalletRepository.class);
        scanner = mock(TonDepositScanner.class);
        service = new TonApiWebhookService(walletRepository, scanner, new AddressNormalizer(), Runnable::run);

        UserEntity user = new UserEntity();
        user.setId(11L);
        wallet = new WalletEntity();
        wallet.setId(22L);
        wallet.setAddress(friendlyAddr);
        wallet.setUserEntity(user);
        when(walletRepository.findByAddressIgnoreCaseAndNetwork(friendlyAddr, BlockchainNetwork.TON))
                .thenReturn(Optional.of(wallet));
    }

    private byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void process_knownAccount_triggersScan() {
        service.process(body("""
                {"account_id":"%s","lt":49739623000001,"tx_hash":"abc"}""".formatted(rawAddr)));

        verify(scanner).scan(wallet);
    }

    @Test
    void process_arrayPayload_triggersScan() {
        service.process(body("""
                [{"account_id":"%s","lt":1,"tx_hash":"abc"}]""".formatted(rawAddr)));

        verify(scanner).scan(wallet);
    }

    @Test
    void process_sameAccountTwice_scansOnce() {
        service.process(body("""
                [{"account_id":"%s","tx_hash":"a"},{"account_id":"%s","tx_hash":"b"}]"""
                .formatted(rawAddr, rawAddr)));

        verify(scanner, times(1)).scan(wallet);
    }

    @Test
    void process_unknownAccount_noScan() {
        String otherRaw = Address.of(
                new TonBlockchainService(mock(TonCenterClient.class), 698983191, "", 60L)
                        .generateWallet().address()).toRaw();
        service.process(body("""
                {"account_id":"%s","tx_hash":"abc"}""".formatted(otherRaw)));

        verify(scanner, never()).scan(any());
    }

    @Test
    void process_malformedAccount_noScan() {
        service.process(body("""
                {"account_id":"not-an-address","tx_hash":"abc"}"""));

        verify(scanner, never()).scan(any());
    }

    @Test
    void process_unparseableBody_noScan() {
        service.process(body("not json"));

        verify(scanner, never()).scan(any());
    }
}
