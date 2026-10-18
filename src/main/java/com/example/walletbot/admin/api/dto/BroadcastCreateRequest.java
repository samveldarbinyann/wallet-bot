package com.example.walletbot.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BroadcastCreateRequest(
        @NotNull @Pattern(regexp = "NEWS|APP_UPDATE") String type,
        @NotBlank @Size(max = 200) String titleRu,
        @NotBlank @Size(max = 200) String titleEn,
        @NotBlank @Size(max = 1000) String bodyRu,
        @NotBlank @Size(max = 1000) String bodyEn,
        @Pattern(regexp = "ALL|WITH_EMAIL") String target,
        String channels,
        String imageUrl
) {
}
