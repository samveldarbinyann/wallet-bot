package com.example.walletbot.platform.web.advice;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.shared.exception.*;
import com.example.walletbot.user.api.dto.TwoFaRequiredResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageService messageService;

    @ExceptionHandler(TwoFaRequiredException.class)
    public ResponseEntity<TwoFaRequiredResponse> handleTwoFaRequired(TwoFaRequiredException e) {
        return ResponseEntity.status(202)
                .body(new TwoFaRequiredResponse(e.getPendingToken(), java.util.Objects.requireNonNullElse(e.getMaskedEmail(), "")));
    }

    private static String getLang() {
        return RequestLocaleResolver.getLang();
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        log.warn("Insufficient funds: {}", e.getMessage());
        return ResponseEntity.status(422)
                .body(new ErrorResponse(messageService.get(getLang(), "error.insufficient_funds"), "INSUFFICIENT_FUNDS", 422, null));
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOtp(InvalidOtpException e) {
        return ResponseEntity.status(422)
                .body(new ErrorResponse(messageService.get(getLang(), "error.invalid_otp"), "INVALID_OTP", 422, null));
    }

    @ExceptionHandler(BlockchainRpcException.class)
    public ResponseEntity<ErrorResponse> handleBlockchainRpc(BlockchainRpcException e) {
        log.warn("Blockchain RPC error: {}", e.getMessage());
        return ResponseEntity.status(502)
                .body(new ErrorResponse(messageService.get(getLang(), "error.internal"), "BLOCKCHAIN_RPC_ERROR", 502, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.status(422)
                .body(new ErrorResponse(messageService.get(getLang(), "error.bad_request"), "VALIDATION_ERROR", 422, fieldErrors));
    }

    @ExceptionHandler(AccountFrozenException.class)
    public ResponseEntity<ErrorResponse> handleAccountFrozen(AccountFrozenException e) {
        return ResponseEntity.status(403)
                .body(new ErrorResponse(
                        messageService.get(getLang(), "error.account_frozen"),
                        "ACCOUNT_FROZEN", 403, null, e.getReason()));
    }

    @ExceptionHandler(AccountBlockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountBlocked(AccountBlockedException e) {
        return ResponseEntity.status(403)
                .body(new ErrorResponse(
                        messageService.get(getLang(), "error.account_blocked"),
                        "ACCOUNT_BLOCKED", 403, null, e.getReason()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse(messageService.get(getLang(), "error.not_found"), "NOT_FOUND", 404, null));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException e) {
        log.warn("Rate limited: {}", e.getMessage());
        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(new ErrorResponse(messageService.get(getLang(), "error.rate_limited"), "RATE_LIMITED", 429, null));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
        log.warn("Bad request: {}", e.getMessageKey());
        String key = e.getMessageKey();
        String msg = (key == null || key.isBlank())
                ? messageService.get(getLang(), "error.bad_request")
                : messageService.get(getLang(), key, e.getArgs());
        return ResponseEntity.status(400)
                .body(new ErrorResponse(msg, "BAD_REQUEST", 400, null));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptLockFailure(org.springframework.orm.ObjectOptimisticLockingFailureException e) {
        log.warn("Concurrent update conflict: {}", e.getMessage());
        return ResponseEntity.status(409)
                .body(new ErrorResponse(messageService.get(getLang(), "error.bad_request"), "CONCURRENT_UPDATE", 409, null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("Upload rejected — too large: {}", e.getMessage());
        return ResponseEntity.status(413)
                .body(new ErrorResponse(messageService.get(getLang(), "error.bad_request"), "FILE_TOO_LARGE", 413, null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request (IAE): {}", e.getMessage());
        return ResponseEntity.status(400)
                .body(new ErrorResponse(messageService.get(getLang(), "error.bad_request"), "BAD_REQUEST", 400, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Unexpected error handling request", e);
        return ResponseEntity.status(500)
                .body(new ErrorResponse(messageService.get(getLang(), "error.internal"), "INTERNAL_ERROR", 500, null));
    }
}
