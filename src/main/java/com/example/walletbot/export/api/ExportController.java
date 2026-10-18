package com.example.walletbot.export.api;

import com.example.walletbot.export.api.dto.*;
import com.example.walletbot.export.application.ExportService;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.user.domain.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/security/export")
@RequiredArgsConstructor
public class ExportController {

    private static final HttpHeaders NO_STORE_HEADERS = buildNoStoreHeaders();

    private final ExportService exportService;
    private final ExportRestMapper exportRestMapper;

    @PostMapping("/request")
    public ResponseEntity<ExportRequestResponse> request(
            @CurrentUser UserEntity user) {
        return ResponseEntity.ok()
                .headers(NO_STORE_HEADERS)
                .body(exportRestMapper.toRestDto(exportService.request(user)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ExportConfirmResponse> confirm(
            @CurrentUser UserEntity user,
            @Valid @RequestBody ExportConfirmRequest req) {
        return ResponseEntity.ok()
                .headers(NO_STORE_HEADERS)
                .body(exportRestMapper.toRestDto(
                        exportService.confirm(user, req.exportPendingToken(), req.otpCode())));
    }

    @PostMapping("/reveal")
    public ResponseEntity<ExportRevealResponse> reveal(
            @CurrentUser UserEntity user,
            @Valid @RequestBody ExportRevealRequest req) {
        return ResponseEntity.ok()
                .headers(NO_STORE_HEADERS)
                .body(exportRestMapper.toRestDto(
                        exportService.reveal(user, req.exportSessionToken(), req.walletId())));
    }

    @PostMapping("/close")
    public ResponseEntity<Void> close(
            @CurrentUser UserEntity user,
            @Valid @RequestBody ExportCloseRequest req) {
        exportService.close(user, req.exportSessionToken());
        return ResponseEntity.noContent().build();
    }

    private static HttpHeaders buildNoStoreHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-store, no-cache, must-revalidate, private");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        headers.add("Vary", "Authorization");
        return HttpHeaders.readOnlyHttpHeaders(headers);
    }
}
