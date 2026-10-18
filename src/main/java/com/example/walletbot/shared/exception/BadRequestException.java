package com.example.walletbot.shared.exception;

import lombok.Getter;

@Getter
public class BadRequestException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;

    public BadRequestException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args == null ? new Object[0] : args;
    }
}
