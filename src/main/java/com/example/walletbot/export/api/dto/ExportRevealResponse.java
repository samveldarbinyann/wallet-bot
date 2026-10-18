package com.example.walletbot.export.api.dto;

import java.time.Instant;

public record ExportRevealResponse(long walletId, String network, String address, String privateKey, String format,
                                   Instant expiresAt) {
    @Override
    public String toString() {
        return "ExportRevealResponse{walletId=" + walletId + ", network=" + network + ", address=" + address + ", privateKey=***, format=" + format + "}";
    }
}