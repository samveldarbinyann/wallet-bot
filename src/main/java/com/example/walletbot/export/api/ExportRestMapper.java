package com.example.walletbot.export.api;

import com.example.walletbot.export.api.dto.ExportConfirmResponse;
import com.example.walletbot.export.api.dto.ExportRequestResponse;
import com.example.walletbot.export.api.dto.ExportRevealResponse;
import com.example.walletbot.export.api.dto.ExportWalletDto;
import com.example.walletbot.export.domain.ExportConfirmDto;
import com.example.walletbot.export.domain.ExportRequestDto;
import com.example.walletbot.export.domain.ExportRevealDto;
import com.example.walletbot.export.domain.ExportWalletInfo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExportRestMapper {

    ExportRequestResponse toRestDto(ExportRequestDto dto);

    ExportConfirmResponse toRestDto(ExportConfirmDto dto);

    ExportRevealResponse toRestDto(ExportRevealDto dto);

    ExportWalletDto toRestDto(ExportWalletInfo info);
}