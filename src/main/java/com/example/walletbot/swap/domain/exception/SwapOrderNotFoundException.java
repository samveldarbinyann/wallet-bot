package com.example.walletbot.swap.domain.exception;

public class SwapOrderNotFoundException extends SwapException {
    public SwapOrderNotFoundException(long orderId) {
        super("Swap order not found: " + orderId);
    }

    @Override
    public String errorCode() {
        return "SWAP_ORDER_NOT_FOUND";
    }
}
