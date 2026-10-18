package com.example.walletbot.swap.application.intent;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.security.IntentTokenCodec;
import com.example.walletbot.swap.domain.SwapFeeBreakdown;
import com.example.walletbot.swap.domain.SwapPair;
import com.example.walletbot.swap.domain.SwapQuote;
import com.example.walletbot.swap.domain.exception.SwapIntentInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwapIntentServiceTest {

    private static final String SECRET = "test-intent-secret-key-at-least-32-chars-long!!";
    private SwapIntentService service;

    @BeforeEach
    void setUp() {
        service = new SwapIntentService(new IntentTokenCodec(SECRET), 600);
    }

    private SwapQuote sampleQuote() {
        return new SwapQuote(
                "simpleswap",
                "quote-1",
                SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.ETH),
                new BigDecimal("0.05"),
                new BigDecimal("0.80"),
                new BigDecimal("0.78"),
                new BigDecimal("16"),
                SwapFeeBreakdown.zero(),
                Instant.now().plusSeconds(60),
                Map.of()
        );
    }

    @Test
    void issueAndVerify_roundTrip() {
        SwapQuote quote = sampleQuote();
        SwapIntentService.IntentToken token = service.issue(42L, quote, "bcAddr", "0xAddr");
        SwapIntent intent = service.verify(token.token(), 42L);

        assertThat(intent.userId()).isEqualTo(42L);
        assertThat(intent.fromNetwork()).isEqualTo(BlockchainNetwork.BTC);
        assertThat(intent.toNetwork()).isEqualTo(BlockchainNetwork.ETH);
        assertThat(intent.amountIn()).isEqualByComparingTo("0.05");
        assertThat(intent.amountOutExpected()).isEqualByComparingTo("0.80");
        assertThat(intent.amountOutMin()).isEqualByComparingTo("0.78");
        assertThat(intent.providerId()).isEqualTo("simpleswap");
        assertThat(intent.providerQuoteId()).isEqualTo("quote-1");
        assertThat(intent.fromAddress()).isEqualTo("bcAddr");
        assertThat(intent.toAddress()).isEqualTo("0xAddr");
        assertThat(intent.jti()).isEqualTo(token.jti());
    }

    @Test
    void verify_wrongUser_throws() {
        SwapIntentService.IntentToken token = service.issue(42L, sampleQuote(), "a", "b");
        assertThatThrownBy(() -> service.verify(token.token(), 99L))
                .isInstanceOf(SwapIntentInvalidException.class);
    }

    @Test
    void verify_garbageToken_throws() {
        assertThatThrownBy(() -> service.verify("not-a-jwt", 42L))
                .isInstanceOf(SwapIntentInvalidException.class);
    }

    @Test
    void verify_signedWithDifferentKey_throws() {
        SwapIntentService other = new SwapIntentService(
                new IntentTokenCodec("different-intent-secret-key-with-32-chars-long!!"), 600);
        SwapIntentService.IntentToken token = other.issue(42L, sampleQuote(), "a", "b");
        assertThatThrownBy(() -> service.verify(token.token(), 42L))
                .isInstanceOf(SwapIntentInvalidException.class);
    }

    @Test
    void verify_blankProviderQuoteId_normalizedToNull() {
        SwapQuote noQuoteId = new SwapQuote("simpleswap", null, sampleQuote().pair(),
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"),
                BigDecimal.ONE, SwapFeeBreakdown.zero(),
                Instant.now().plusSeconds(60), Map.of());
        SwapIntentService.IntentToken token = service.issue(1L, noQuoteId, "a", "b");

        assertThat(service.verify(token.token(), 1L).providerQuoteId()).isNull();
    }

    @Test
    void verify_expiredToken_throws() {
        SwapIntentService shortLived = new SwapIntentService(new IntentTokenCodec(SECRET), -1);
        SwapIntentService.IntentToken token = shortLived.issue(42L, sampleQuote(), "a", "b");

        assertThatThrownBy(() -> shortLived.verify(token.token(), 42L))
                .isInstanceOf(SwapIntentInvalidException.class);
    }
}
