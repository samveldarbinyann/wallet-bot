package com.example.walletbot.swap.domain.exception;

public class SwapIdempotencyConflictException extends SwapException {
    public SwapIdempotencyConflictException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "SWAP_IDEMPOTENCY_CONFLICT";
    }
}
