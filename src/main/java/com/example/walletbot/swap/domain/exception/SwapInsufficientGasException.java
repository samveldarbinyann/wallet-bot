package com.example.walletbot.swap.domain.exception;

public class SwapInsufficientGasException extends SwapException {
    public SwapInsufficientGasException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "SWAP_INSUFFICIENT_GAS";
    }
}
