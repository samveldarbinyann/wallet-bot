package com.example.walletbot.user.api.dto;

public record PinPendingResponse(String type, String pinPendingToken, String maskedEmail) {
    public PinPendingResponse(String pinPendingToken, String maskedEmail) {
        this("PIN_PENDING", pinPendingToken, maskedEmail);
    }
}