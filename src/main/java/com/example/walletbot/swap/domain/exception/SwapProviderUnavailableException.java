package com.example.walletbot.swap.domain.exception;

public class SwapProviderUnavailableException extends SwapException {
    public SwapProviderUnavailableException(String message) {
        super(message);
    }

    public SwapProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String errorCode() {
        return "SWAP_PROVIDER_UNAVAILABLE";
    }
}
