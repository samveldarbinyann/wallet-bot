package com.example.walletbot.export.domain;

import java.util.List;

public record ExportWalletInfo(
        long walletId,
        String network,
        String address,
        List<String> alsoCovers
) {
}