package com.example.walletbot.transaction.domain;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.util.AddressNormalizer;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.AddressPolicyService;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferValidatorTest {

    private static final BlockchainNetwork NET = BlockchainNetwork.USDT_TRC20;

    @Mock
    WalletService walletService;
    @Mock
    BlockchainServiceFactory blockchainFactory;
    @Mock
    AddressPolicyService addressPolicyService;
    @Mock
    AddressNormalizer normalizer;
    @Mock
    BlockchainService blockchainService;

    @InjectMocks
    TransferValidator transferValidator;

    private UserEntity user;
    private WalletEntity wallet;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);

        wallet = new WalletEntity();
        wallet.setNetwork(NET);
        wallet.setAddress("Towner");

        when(blockchainFactory.getService(NET)).thenReturn(blockchainService);
        when(blockchainService.isSendSupported()).thenReturn(true);
        when(walletService.getWalletForNetwork(user, NET)).thenReturn(wallet);
        when(normalizer.sameAddress(eq(NET), eq("Towner"), anyString())).thenReturn(false);
        when(normalizer.canonical(eq(NET), anyString())).thenAnswer(inv -> inv.getArgument(1));
        doNothing().when(addressPolicyService).requireValid(any(), any());
    }

    @Test
    void validate_happyPath_returnsTarget() {
        when(normalizer.canonical(NET, "Trecipient")).thenReturn("Trecipient_canonical");

        TransferValidator.Target target =
                transferValidator.validate(user, NET, "Trecipient", new BigDecimal("20"));

        assertThat(target.wallet()).isSameAs(wallet);
        assertThat(target.canonicalTo()).isEqualTo("Trecipient_canonical");
    }

    @Test
    void validate_sendNotSupported_throwsBadRequest() {
        when(blockchainService.isSendSupported()).thenReturn(false);

        assertThatThrownBy(() -> transferValidator.validate(user, NET, "Trecipient", new BigDecimal("10")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.send_not_supported");
    }

    @Test
    void validate_noWallet_throwsBadRequest() {
        when(walletService.getWalletForNetwork(user, NET)).thenReturn(null);

        assertThatThrownBy(() -> transferValidator.validate(user, NET, "Trecipient", new BigDecimal("10")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.no_wallet_for_network");
    }

    @Test
    void validate_selfAddress_throwsBadRequest() {
        when(normalizer.sameAddress(NET, "Towner", "Towner")).thenReturn(true);

        assertThatThrownBy(() -> transferValidator.validate(user, NET, "Towner", new BigDecimal("10")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.send_to_own_address");
    }

    @Test
    void validate_nullAmount_throwsBadRequest() {
        assertThatThrownBy(() -> transferValidator.validate(user, NET, "Trecipient", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.amount_not_positive");
    }

    @Test
    void validate_zeroAmount_throwsBadRequest() {
        assertThatThrownBy(() -> transferValidator.validate(user, NET, "Trecipient", BigDecimal.ZERO))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.amount_not_positive");
    }

    @Test
    void validate_negativeAmount_throwsBadRequest() {
        assertThatThrownBy(() -> transferValidator.validate(user, NET, "Trecipient", new BigDecimal("-1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.amount_not_positive");
    }

    @Test
    void validate_amountBelowMinimum_throwsBadRequest() {
        BlockchainNetwork btc = BlockchainNetwork.BTC;
        WalletEntity btcWallet = new WalletEntity();
        btcWallet.setNetwork(btc);
        btcWallet.setAddress("1btcOwner");
        when(blockchainFactory.getService(btc)).thenReturn(blockchainService);
        when(blockchainService.isSendSupported()).thenReturn(true);
        when(walletService.getWalletForNetwork(user, btc)).thenReturn(btcWallet);
        when(normalizer.sameAddress(eq(btc), anyString(), anyString())).thenReturn(false);
        doNothing().when(addressPolicyService).requireValid(eq(btc), any());

        assertThatThrownBy(() -> transferValidator.validate(user, btc, "1btcRecipient", new BigDecimal("0.000001")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.amount_below_minimum");
    }

    @Test
    void validate_amountAboveMax_throwsBadRequest() {
        assertThatThrownBy(() -> transferValidator.validate(user, NET, "Trecipient", new BigDecimal("2000000000")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.amount_above_maximum");
    }

    @Test
    void validate_tooManyDecimals_throwsBadRequest() {
        assertThatThrownBy(() -> transferValidator.validate(user, NET, "Trecipient", new BigDecimal("1.0000001")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.amount_decimal_overflow");
    }

    @Test
    void validate_exactlyMinSend_passes() {
        TransferValidator.Target target =
                transferValidator.validate(user, NET, "Trecipient", new BigDecimal("15"));
        assertThat(target).isNotNull();
    }

    @Test
    void validate_exactlyMaxSend_passes() {
        TransferValidator.Target target =
                transferValidator.validate(user, NET, "Trecipient", new BigDecimal("1000000000"));
        assertThat(target).isNotNull();
    }
}
