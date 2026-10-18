package com.example.walletbot.swap.domain.exception;

public class SwapQuoteUnavailableException extends SwapException {
    public SwapQuoteUnavailableException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "SWAP_QUOTE_UNAVAILABLE";
    }
}
