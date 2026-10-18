package com.example.walletbot.swap.api;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.platform.web.advice.ErrorResponse;
import com.example.walletbot.swap.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwapExceptionHandlerTest {

    private SwapExceptionHandler handler;

    @BeforeEach
    void setUp() {
        MessageService messages = mock(MessageService.class);
        when(messages.get(anyString(), anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(1));
        handler = new SwapExceptionHandler(messages);
    }

    @Test
    void quoteUnavailable_422() {
        ResponseEntity<ErrorResponse> r = handler.handleQuoteUnavailable(
                new SwapQuoteUnavailableException("down"));
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(r.getBody().code()).isEqualTo("SWAP_QUOTE_UNAVAILABLE");
    }

    @Test
    void belowMin_400_withDetails() {
        ResponseEntity<ErrorResponse> r = handler.handleBelowMin(
                new SwapAmountBelowMinException(new BigDecimal("5"), "USDT"));
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody().code()).isEqualTo("SWAP_AMOUNT_BELOW_MIN");
        assertThat(r.getBody().fieldErrors()).containsEntry("min", "5").containsEntry("symbol", "USDT");
    }

    @Test
    void aboveMax_400_withDetails() {
        ResponseEntity<ErrorResponse> r = handler.handleAboveMax(
                new SwapAmountAboveMaxException(new BigDecimal("9000"), "BTC"));
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody().code()).isEqualTo("SWAP_AMOUNT_ABOVE_MAX");
        assertThat(r.getBody().fieldErrors()).containsEntry("max", "9000").containsEntry("symbol", "BTC");
    }

    @Test
    void intentInvalid_401() {
        ResponseEntity<ErrorResponse> r = handler.handleIntent(new SwapIntentInvalidException("bad"));
        assertThat(r.getStatusCode().value()).isEqualTo(401);
        assertThat(r.getBody().code()).isEqualTo("SWAP_INTENT_INVALID");
    }

    @Test
    void insufficientGas_422() {
        ResponseEntity<ErrorResponse> r = handler.handleGas(new SwapInsufficientGasException("no gas"));
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(r.getBody().code()).isEqualTo("SWAP_INSUFFICIENT_GAS");
    }

    @Test
    void providerUnavailable_503() {
        ResponseEntity<ErrorResponse> r = handler.handleProviderDown(
                new SwapProviderUnavailableException("down"));
        assertThat(r.getStatusCode().value()).isEqualTo(503);
        assertThat(r.getBody().code()).isEqualTo("SWAP_PROVIDER_UNAVAILABLE");
    }

    @Test
    void orderNotFound_404() {
        ResponseEntity<ErrorResponse> r = handler.handleNotFound(new SwapOrderNotFoundException(7L));
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody().code()).isEqualTo("SWAP_ORDER_NOT_FOUND");
    }

    @Test
    void idempotencyConflict_409() {
        ResponseEntity<ErrorResponse> r = handler.handleIdem(
                new SwapIdempotencyConflictException("dup"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(r.getBody().code()).isEqualTo("SWAP_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void unmappedSwapException_500() {
        SwapException custom = new SwapException("weird") {
            @Override
            public String errorCode() {
                return "SWAP_WEIRD";
            }
        };
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(custom);
        assertThat(r.getStatusCode().value()).isEqualTo(500);
        assertThat(r.getBody().code()).isEqualTo("SWAP_WEIRD");
    }
}
