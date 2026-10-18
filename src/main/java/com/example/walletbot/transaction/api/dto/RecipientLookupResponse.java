package com.example.walletbot.transaction.api.dto;

public record RecipientLookupResponse(
        boolean found,
        String username,
        String displayName,
        boolean self
) {
}