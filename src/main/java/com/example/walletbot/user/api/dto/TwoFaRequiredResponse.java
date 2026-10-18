package com.example.walletbot.user.api.dto;

public record TwoFaRequiredResponse(
        String type,
        String pendingToken,
        String maskedEmail
) {
    public TwoFaRequiredResponse(String pendingToken, String maskedEmail) {
        this("2FA_PENDING", pendingToken, maskedEmail);
    }
}