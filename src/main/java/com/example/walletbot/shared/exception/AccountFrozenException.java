package com.example.walletbot.shared.exception;

import lombok.Getter;

@Getter
public class AccountFrozenException extends RuntimeException {

    private final String reason;

    public AccountFrozenException(String reason) {
        super("Account is frozen");
        this.reason = reason;
    }
}
