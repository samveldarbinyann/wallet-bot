package com.example.walletbot.swap.domain.exception;

public abstract class SwapException extends RuntimeException {
    protected SwapException(String message) {
        super(message);
    }

    protected SwapException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract String errorCode();
}
