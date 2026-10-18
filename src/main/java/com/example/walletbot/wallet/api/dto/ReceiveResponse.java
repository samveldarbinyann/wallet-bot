package com.example.walletbot.wallet.api.dto;

public record ReceiveResponse(
        String address,
        String network,
        String qrCodeBase64
) {
}
