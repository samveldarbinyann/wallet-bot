package com.example.walletbot.swap.domain.exception;

public class SwapIntentInvalidException extends SwapException {
    public SwapIntentInvalidException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "SWAP_INTENT_INVALID";
    }
}
