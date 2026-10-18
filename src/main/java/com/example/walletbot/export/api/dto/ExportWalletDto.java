package com.example.walletbot.export.api.dto;

import java.util.List;

public record ExportWalletDto(long walletId, String network, String address, List<String> alsoCovers) {
}