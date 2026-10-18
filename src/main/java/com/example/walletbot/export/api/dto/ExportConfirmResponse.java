package com.example.walletbot.export.api.dto;

import java.util.List;

public record ExportConfirmResponse(String exportSessionToken, List<ExportWalletDto> wallets) {
}