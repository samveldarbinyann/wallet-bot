package com.example.walletbot.swap.api;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.platform.web.advice.ErrorResponse;
import com.example.walletbot.platform.web.advice.RequestLocaleResolver;
import com.example.walletbot.swap.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.example.walletbot.swap")
@RequiredArgsConstructor
public class SwapExceptionHandler {

    private final MessageService messageService;

    private String t(String key, Object... args) {
        return messageService.get(RequestLocaleResolver.getLang(), key, args);
    }

    @ExceptionHandler(SwapQuoteUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleQuoteUnavailable(SwapQuoteUnavailableException e) {
        log.warn("Swap quote unavailable: {}", e.getMessage());
        return ResponseEntity.status(422)
                .body(new ErrorResponse(t("error.swap_quote_unavailable"), e.errorCode(), 422, null));
    }

    @ExceptionHandler(SwapAmountBelowMinException.class)
    public ResponseEntity<ErrorResponse> handleBelowMin(SwapAmountBelowMinException e) {
        log.warn("Swap amount below min: {}", e.getMessage());
        Map<String, String> details = new HashMap<>();
        details.put("min", e.getMinAmount().toPlainString());
        details.put("symbol", e.getSymbol());
        return ResponseEntity.status(400)
                .body(new ErrorResponse(
                        t("error.swap_amount_below_min", e.getMinAmount().toPlainString(), e.getSymbol()),
                        e.errorCode(), 400, details));
    }

    @ExceptionHandler(SwapAmountAboveMaxException.class)
    public ResponseEntity<ErrorResponse> handleAboveMax(SwapAmountAboveMaxException e) {
        log.warn("Swap amount above max: {}", e.getMessage());
        Map<String, String> details = new HashMap<>();
        details.put("max", e.getMaxAmount().toPlainString());
        details.put("symbol", e.getSymbol());
        return ResponseEntity.status(400)
                .body(new ErrorResponse(
                        t("error.swap_amount_above_max", e.getMaxAmount().toPlainString(), e.getSymbol()),
                        e.errorCode(), 400, details));
    }

    @ExceptionHandler(SwapIntentInvalidException.class)
    public ResponseEntity<ErrorResponse> handleIntent(SwapIntentInvalidException e) {
        log.warn("Swap intent invalid: {}", e.getMessage());
        return ResponseEntity.status(401)
                .body(new ErrorResponse(t("error.swap_intent_invalid"), e.errorCode(), 401, null));
    }

    @ExceptionHandler(SwapInsufficientGasException.class)
    public ResponseEntity<ErrorResponse> handleGas(SwapInsufficientGasException e) {
        log.warn("Swap insufficient gas: {}", e.getMessage());
        return ResponseEntity.status(422)
                .body(new ErrorResponse(t("error.swap_insufficient_gas"), e.errorCode(), 422, null));
    }

    @ExceptionHandler(SwapProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleProviderDown(SwapProviderUnavailableException e) {
        log.warn("Swap provider unavailable: {}", e.getMessage());
        return ResponseEntity.status(503)
                .body(new ErrorResponse(t("error.swap_provider_unavailable"), e.errorCode(), 503, null));
    }

    @ExceptionHandler(SwapOrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SwapOrderNotFoundException e) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse(t("error.swap_order_not_found"), e.errorCode(), 404, null));
    }

    @ExceptionHandler(SwapIdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdem(SwapIdempotencyConflictException e) {
        log.warn("Swap idempotency conflict: {}", e.getMessage());
        return ResponseEntity.status(409)
                .body(new ErrorResponse(t("error.swap_idempotency_conflict"), e.errorCode(), 409, null));
    }

    @ExceptionHandler(SwapException.class)
    public ResponseEntity<ErrorResponse> handleGeneric(SwapException e) {
        log.warn("Unmapped SwapException ({}): {}", e.errorCode(), e.getMessage());
        return ResponseEntity.status(500)
                .body(new ErrorResponse(t("error.swap_failed"), e.errorCode(), 500, null));
    }
}
