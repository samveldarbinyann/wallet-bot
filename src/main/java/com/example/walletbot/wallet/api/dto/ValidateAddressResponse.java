package com.example.walletbot.wallet.api.dto;

public record ValidateAddressResponse(
        boolean valid,
        String hint
) {
}
