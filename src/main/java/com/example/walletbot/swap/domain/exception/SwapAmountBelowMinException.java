package com.example.walletbot.swap.domain.exception;

import java.math.BigDecimal;

public class SwapAmountBelowMinException extends SwapException {
    private final BigDecimal minAmount;
    private final String symbol;

    public SwapAmountBelowMinException(BigDecimal minAmount, String symbol) {
        super("Amount is below minimum " + minAmount.toPlainString() + " " + symbol);
        this.minAmount = minAmount;
        this.symbol = symbol;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public String errorCode() {
        return "SWAP_AMOUNT_BELOW_MIN";
    }
}
