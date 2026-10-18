package com.example.walletbot.shared.exception;

import lombok.Getter;

@Getter
public class AccountBlockedException extends RuntimeException {

    private final String reason;

    public AccountBlockedException(String reason) {
        super("Account is blocked");
        this.reason = reason;
    }
}
