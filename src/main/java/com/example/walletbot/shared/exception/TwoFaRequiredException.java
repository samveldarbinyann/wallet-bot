package com.example.walletbot.shared.exception;

import lombok.Getter;

@Getter
public class TwoFaRequiredException extends RuntimeException {

    private final String pendingToken;
    private final String maskedEmail;

    public TwoFaRequiredException(String pendingToken, String maskedEmail) {
        super("2FA verification required");
        this.pendingToken = pendingToken;
        this.maskedEmail = maskedEmail;
    }
}