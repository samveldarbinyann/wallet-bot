package com.example.walletbot.admin.api;

import com.example.walletbot.admin.api.dto.BroadcastCreateRequest;
import com.example.walletbot.admin.api.dto.BroadcastDto;
import com.example.walletbot.admin.api.dto.BroadcastImageUploadResponse;
import com.example.walletbot.admin.application.AdminBroadcastService;
import com.example.walletbot.platform.web.security.AdminMutationRateLimiter;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.user.domain.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/broadcasts")
@RequiredArgsConstructor
public class AdminBroadcastController {

    private final AdminBroadcastService broadcastService;
    private final AdminMutationRateLimiter mutationRateLimiter;
    private final BroadcastRestMapper broadcastRestMapper;

    @GetMapping
    public Page<BroadcastDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return broadcastService.list(page, size).map(broadcastRestMapper::toRestDto);
    }

    @PostMapping
    public ResponseEntity<BroadcastDto> create(
            @CurrentUser UserEntity admin,
            @Valid @RequestBody BroadcastCreateRequest req) {

        mutationRateLimiter.assertAllowed(admin.getId());
        var dto = broadcastService.create(
                admin.getId(), req.type(),
                req.titleRu(), req.titleEn(),
                req.bodyRu(), req.bodyEn(),
                req.target(), req.channels(), req.imageUrl());
        return ResponseEntity.ok(broadcastRestMapper.toRestDto(dto));
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BroadcastImageUploadResponse> uploadImage(
            @CurrentUser UserEntity admin,
            @RequestPart("file") MultipartFile file) {

        mutationRateLimiter.assertAllowed(admin.getId());
        return ResponseEntity.ok(new BroadcastImageUploadResponse(broadcastService.uploadImage(file)));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<BroadcastDto> send(
            @CurrentUser UserEntity admin,
            @PathVariable Long id) {

        mutationRateLimiter.assertAllowed(admin.getId());
        return ResponseEntity.ok(broadcastRestMapper.toRestDto(broadcastService.send(id)));
    }

    @GetMapping("/{id}")
    public BroadcastDto get(@PathVariable Long id) {
        return broadcastRestMapper.toRestDto(broadcastService.get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @CurrentUser UserEntity admin,
            @PathVariable Long id) {

        mutationRateLimiter.assertAllowed(admin.getId());
        broadcastService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
