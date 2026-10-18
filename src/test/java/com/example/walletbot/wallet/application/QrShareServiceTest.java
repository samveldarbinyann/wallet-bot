package com.example.walletbot.wallet.application;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.InlineMessagePreparer;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.infrastructure.QrShareTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QrShareServiceTest {

    @Mock
    WalletService walletService;
    @Mock
    QrShareTokenStore tokenStore;
    @Mock
    InlineMessagePreparer inlineMessagePreparer;
    @Mock
    MessageService messageService;

    @InjectMocks
    QrShareService service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "publicUrl", "https://wallet.example.com/");
        user = new UserEntity();
        user.setId(1L);
        user.setTelegramId(100L);
    }

    @Test
    void prepareShare_noWallet_throws() {
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(null);

        assertThatThrownBy(() -> service.prepareShare(user, BlockchainNetwork.ETH))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.no_wallet_for_network");
    }

    @Test
    void prepareShare_buildsPhotoUrlWithoutDoubleSlashAndReturnsMessageId() {
        WalletEntity wallet = new WalletEntity();
        wallet.setAddress("0xABC");
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet);
        when(tokenStore.store("0xABC")).thenReturn("tok123");
        when(inlineMessagePreparer.preparePhotoMessage(eq(100L), eq("https://wallet.example.com/api/files/qr/tok123"), any()))
                .thenReturn("msg-1");

        String result = service.prepareShare(user, BlockchainNetwork.ETH);

        assertThat(result).isEqualTo("msg-1");
        verify(tokenStore).store("0xABC");
    }

    @Test
    void prepareShare_prepareFails_throws() {
        WalletEntity wallet = new WalletEntity();
        wallet.setAddress("0xABC");
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet);
        when(tokenStore.store("0xABC")).thenReturn("tok123");
        when(inlineMessagePreparer.preparePhotoMessage(any(), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.prepareShare(user, BlockchainNetwork.ETH))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.share_prepare_failed");
    }
}
