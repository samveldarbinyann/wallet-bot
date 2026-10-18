package com.example.walletbot.export.domain;

import java.time.Instant;

public record ExportRevealDto(
        long walletId,
        String network,
        String address,
        String privateKey,
        String format,
        Instant expiresAt
) {
    @Override
    public String toString() {
        return "ExportRevealDto{walletId=" + walletId + ", network=" + network
                + ", address=" + address + ", privateKey=***, format=" + format
                + ", expiresAt=" + expiresAt + "}";
    }
}