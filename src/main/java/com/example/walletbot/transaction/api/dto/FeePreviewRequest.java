package com.example.walletbot.transaction.api.dto;

import com.example.walletbot.platform.web.validation.NetworkName;

public record FeePreviewRequest(
        @NetworkName
        String network
) {
}
