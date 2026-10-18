package com.example.walletbot.wallet.application;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceTest {

    @Mock
    WalletRepository walletRepository;
    @Mock
    BlockchainServiceFactory blockchainFactory;
    @Mock
    MessageService messageService;
    @Mock
    BlockchainService blockchainService;

    @InjectMocks
    WalletService walletService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setTelegramId(100L);

        when(blockchainFactory.getService(any())).thenReturn(blockchainService);
        when(blockchainService.generateWallet())
                .thenReturn(new BlockchainService.WalletKeys("addr", "privkey"));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createAllWallets_noExistingWallets_createsOnePerBaseNetwork() {
        when(walletRepository.findByUserEntityId(1L)).thenReturn(Collections.emptyList());

        walletService.createAllWallets(user);

        verify(walletRepository, times(5)).save(any(WalletEntity.class));
    }

    @Test
    void createAllWallets_walletAlreadyExists_skipsNetwork() {
        WalletEntity existing = walletEntity(BlockchainNetwork.ETH);
        when(walletRepository.findByUserEntityId(1L)).thenReturn(List.of(existing));

        walletService.createAllWallets(user);

        verify(walletRepository, times(4)).save(any(WalletEntity.class));
    }

    @Test
    void createAllWallets_blockchainThrows_continuesOtherNetworks() {
        when(walletRepository.findByUserEntityId(1L)).thenReturn(Collections.emptyList());
        when(blockchainService.generateWallet())
                .thenThrow(new RuntimeException("RPC error"))
                .thenReturn(new BlockchainService.WalletKeys("addr2", "pk2"))
                .thenReturn(new BlockchainService.WalletKeys("addr3", "pk3"))
                .thenReturn(new BlockchainService.WalletKeys("addr4", "pk4"))
                .thenReturn(new BlockchainService.WalletKeys("addr5", "pk5"));

        walletService.createAllWallets(user);

        verify(walletRepository, times(4)).save(any(WalletEntity.class));
    }

    @Test
    void getWalletForNetwork_directMatch_returnsWallet() {
        WalletEntity ethWallet = walletEntity(BlockchainNetwork.ETH);
        when(walletRepository.findByUserEntityId(1L)).thenReturn(List.of(ethWallet));

        WalletEntity result = walletService.getWalletForNetwork(user, BlockchainNetwork.ETH);

        assertThat(result).isSameAs(ethWallet);
    }

    @Test
    void getWalletForNetwork_bnbNetwork_returnsEthWallet() {
        WalletEntity ethWallet = walletEntity(BlockchainNetwork.ETH);
        when(walletRepository.findByUserEntityId(1L)).thenReturn(List.of(ethWallet));

        WalletEntity result = walletService.getWalletForNetwork(user, BlockchainNetwork.BNB);

        assertThat(result).isSameAs(ethWallet);
    }

    @Test
    void getWalletForNetwork_usdtErc20_returnsEthWallet() {
        WalletEntity ethWallet = walletEntity(BlockchainNetwork.ETH);
        when(walletRepository.findByUserEntityId(1L)).thenReturn(List.of(ethWallet));

        WalletEntity result = walletService.getWalletForNetwork(user, BlockchainNetwork.USDT_ERC20);

        assertThat(result).isSameAs(ethWallet);
    }

    @Test
    void getWalletForNetwork_usdtTrc20_returnsTrxWallet() {
        WalletEntity trxWallet = walletEntity(BlockchainNetwork.TRX);
        when(walletRepository.findByUserEntityId(1L)).thenReturn(List.of(trxWallet));

        WalletEntity result = walletService.getWalletForNetwork(user, BlockchainNetwork.USDT_TRC20);

        assertThat(result).isSameAs(trxWallet);
    }

    @Test
    void getWalletForNetwork_bep20Token_resolvesThroughBnbToEthWallet() {
        WalletEntity ethWallet = walletEntity(BlockchainNetwork.ETH);
        when(walletRepository.findByUserEntityId(1L)).thenReturn(List.of(ethWallet));

        assertThat(walletService.getWalletForNetwork(user, BlockchainNetwork.USDT_BEP20)).isSameAs(ethWallet);
        assertThat(walletService.getWalletForNetwork(user, BlockchainNetwork.USDC_BEP20)).isSameAs(ethWallet);
    }

    @Test
    void getWalletForNetwork_nullNetwork_returnsNull() {
        WalletEntity result = walletService.getWalletForNetwork(user, null);

        assertThat(result).isNull();
        verifyNoInteractions(walletRepository);
    }

    @Test
    void getWalletForNetwork_noWallets_returnsNull() {
        when(walletRepository.findByUserEntityId(1L)).thenReturn(Collections.emptyList());

        WalletEntity result = walletService.getWalletForNetwork(user, BlockchainNetwork.BTC);

        assertThat(result).isNull();
    }

    @Test
    void groupByNetwork_aliasesBnbToEthWallet() {
        WalletEntity ethWallet = walletEntity(BlockchainNetwork.ETH);
        WalletEntity btcWallet = walletEntity(BlockchainNetwork.BTC);

        Map<BlockchainNetwork, WalletEntity> byNetwork =
                walletService.groupByNetwork(List.of(ethWallet, btcWallet));

        assertThat(byNetwork.get(BlockchainNetwork.ETH)).isSameAs(ethWallet);
        assertThat(byNetwork.get(BlockchainNetwork.BNB)).isSameAs(ethWallet);
        assertThat(byNetwork.get(BlockchainNetwork.BTC)).isSameAs(btcWallet);
    }

    @Test
    void groupByNetwork_noEthWallet_noBnbAlias() {
        WalletEntity btcWallet = walletEntity(BlockchainNetwork.BTC);

        Map<BlockchainNetwork, WalletEntity> byNetwork =
                walletService.groupByNetwork(List.of(btcWallet));

        assertThat(byNetwork).doesNotContainKey(BlockchainNetwork.BNB);
    }

    @Test
    void validateAddress_validAddress_returnsValidTrue() {
        when(blockchainService.isValidAddress("0xABCD")).thenReturn(true);

        WalletService.AddressValidation result =
                walletService.validateAddress(BlockchainNetwork.ETH, "0xABCD", "en");

        assertThat(result.valid()).isTrue();
        assertThat(result.hint()).isNull();
    }

    @Test
    void validateAddress_invalidAddress_returnsFalseWithHint() {
        when(blockchainService.isValidAddress("bad")).thenReturn(false);
        when(messageService.get("en", "wallet.send.hint.evm")).thenReturn("EVM hint");

        WalletService.AddressValidation result =
                walletService.validateAddress(BlockchainNetwork.ETH, "bad", "en");

        assertThat(result.valid()).isFalse();
        assertThat(result.hint()).isEqualTo("EVM hint");
    }

    private WalletEntity walletEntity(BlockchainNetwork network) {
        WalletEntity w = new WalletEntity();
        w.setNetwork(network);
        w.setAddress("addr-" + network.name());
        w.setPrivateKey("pk-" + network.name());
        return w;
    }
}
