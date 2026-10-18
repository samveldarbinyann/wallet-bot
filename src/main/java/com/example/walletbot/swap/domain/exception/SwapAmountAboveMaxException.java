package com.example.walletbot.swap.domain.exception;

import java.math.BigDecimal;

public class SwapAmountAboveMaxException extends SwapException {
    private final BigDecimal maxAmount;
    private final String symbol;

    public SwapAmountAboveMaxException(BigDecimal maxAmount, String symbol) {
        super("Amount is above maximum " + maxAmount.toPlainString() + " " + symbol);
        this.maxAmount = maxAmount;
        this.symbol = symbol;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public String errorCode() {
        return "SWAP_AMOUNT_ABOVE_MAX";
    }
}
