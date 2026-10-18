package com.example.walletbot.export.domain;

import java.util.List;

public record ExportConfirmDto(
        String exportSessionToken,
        List<ExportWalletInfo> wallets
) {
}