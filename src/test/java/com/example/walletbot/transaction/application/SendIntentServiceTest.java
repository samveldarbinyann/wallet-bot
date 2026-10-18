package com.example.walletbot.transaction.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.security.IntentTokenCodec;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SendIntentServiceTest {

    private static final String SECRET = "test-intent-secret-at-least-32-bytes-long!!";

    private SendIntentService service;
    private WalletService walletService;

    @BeforeEach
    void setUp() {
        service = new SendIntentService(new IntentTokenCodec(SECRET));
        walletService = mock(WalletService.class);
        service.setWalletService(walletService);
    }

    @Test
    void createAndVerify_roundTrip() {
        String token = service.create(1L, 50L, BlockchainNetwork.ETH, "0xRecipient", new BigDecimal("1.5"), false);

        SendIntentService.SendIntent intent = service.verify(token, 1L);

        assertThat(intent.walletId()).isEqualTo(50L);
        assertThat(intent.network()).isEqualTo(BlockchainNetwork.ETH);
        assertThat(intent.canonicalTo()).isEqualTo("0xRecipient");
        assertThat(intent.amount()).isEqualByComparingTo("1.5");
        assertThat(intent.max()).isFalse();
        assertThat(intent.jti()).isNotBlank();
    }

    @Test
    void createAndVerify_maxFlag_roundTrips() {
        String token = service.create(1L, 50L, BlockchainNetwork.ETH, "0xRecipient", new BigDecimal("1.5"), true);

        SendIntentService.SendIntent intent = service.verify(token, 1L);

        assertThat(intent.max()).isTrue();
    }

    @Test
    void verify_userMismatch_throwsBadRequest() {
        String token = service.create(1L, 50L, BlockchainNetwork.ETH, "0xR", BigDecimal.ONE, false);

        assertThatThrownBy(() -> service.verify(token, 999L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.intent_token_user_mismatch");
    }

    @Test
    void verify_tamperedToken_throwsBadRequest() {
        String token = service.create(1L, 50L, BlockchainNetwork.ETH, "0xR", BigDecimal.ONE, false);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> service.verify(tampered, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.intent_token_invalid_or_expired");
    }

    @Test
    void verify_tokenSignedWithDifferentSecret_rejected() {
        SendIntentService other = new SendIntentService(
                new IntentTokenCodec("a-totally-different-secret-also-32-bytes!!"));
        String token = other.create(1L, 50L, BlockchainNetwork.ETH, "0xR", BigDecimal.ONE, false);

        assertThatThrownBy(() -> service.verify(token, 1L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resolve_matchingWallet_returnsResolvedTransfer() {
        String token = service.create(1L, 50L, BlockchainNetwork.ETH, "0xR", new BigDecimal("2"), false);
        UserEntity user = new UserEntity();
        user.setId(1L);
        WalletEntity wallet = new WalletEntity();
        wallet.setId(50L);
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet);

        SendIntentService.ResolvedTransfer resolved = service.resolve(user, token);

        assertThat(resolved.wallet()).isSameAs(wallet);
        assertThat(resolved.net()).isEqualTo(BlockchainNetwork.ETH);
        assertThat(resolved.canonicalTo()).isEqualTo("0xR");
    }

    @Test
    void resolve_walletMismatch_throwsBadRequest() {
        String token = service.create(1L, 50L, BlockchainNetwork.ETH, "0xR", new BigDecimal("2"), false);
        UserEntity user = new UserEntity();
        user.setId(1L);
        WalletEntity wallet = new WalletEntity();
        wallet.setId(99L);
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet);

        assertThatThrownBy(() -> service.resolve(user, token))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.intent_wallet_mismatch");
    }

    @Test
    void resolve_noWallet_throwsBadRequest() {
        String token = service.create(1L, 50L, BlockchainNetwork.ETH, "0xR", new BigDecimal("2"), false);
        UserEntity user = new UserEntity();
        user.setId(1L);
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(null);

        assertThatThrownBy(() -> service.resolve(user, token))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.intent_wallet_mismatch");
    }
}
