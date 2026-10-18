package com.example.walletbot.admin.api;

import com.example.walletbot.admin.api.dto.AdminStatusChangeRequest;
import com.example.walletbot.admin.api.dto.AdminUserListItemDto;
import com.example.walletbot.admin.application.AdminUserMapper;
import com.example.walletbot.admin.application.AdminUserService;
import com.example.walletbot.platform.web.security.AdminMutationRateLimiter;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.user.domain.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminMutationRateLimiter mutationRateLimiter;
    private final AdminUserMapper adminUserMapper;
    private final AdminUserRestMapper adminUserRestMapper;

    @GetMapping
    public Page<AdminUserListItemDto> listUsers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UserStatus statusEnum = status != null ? UserStatus.valueOf(status.toUpperCase()) : null;
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return adminUserService.listUsers(statusEnum, q, pageable).map(this::toRestDto);
    }

    @GetMapping("/{id}")
    public AdminUserListItemDto getUser(@PathVariable Long id) {
        return toRestDto(adminUserService.getUser(id));
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<AdminUserListItemDto> blockUser(
            @CurrentUser UserEntity admin,
            @PathVariable Long id,
            @RequestBody(required = false) AdminStatusChangeRequest body) {

        mutationRateLimiter.assertAllowed(admin.getId());
        String reason = body != null ? body.reason() : null;
        UserEntity updated = adminUserService.blockUser(admin.getId(), id, reason);
        return ResponseEntity.ok(toRestDto(updated));
    }

    @PostMapping("/{id}/freeze")
    public ResponseEntity<AdminUserListItemDto> freezeUser(
            @CurrentUser UserEntity admin,
            @PathVariable Long id,
            @RequestBody(required = false) AdminStatusChangeRequest body) {

        mutationRateLimiter.assertAllowed(admin.getId());
        String reason = body != null ? body.reason() : null;
        UserEntity updated = adminUserService.freezeUser(admin.getId(), id, reason);
        return ResponseEntity.ok(toRestDto(updated));
    }

    @PostMapping("/{id}/unblock")
    public ResponseEntity<AdminUserListItemDto> unblockUser(
            @CurrentUser UserEntity admin,
            @PathVariable Long id) {

        mutationRateLimiter.assertAllowed(admin.getId());
        UserEntity updated = adminUserService.unblockUser(admin.getId(), id);
        return ResponseEntity.ok(toRestDto(updated));
    }

    @PostMapping("/{id}/force-logout")
    public ResponseEntity<Map<String, Boolean>> forceLogout(
            @CurrentUser UserEntity admin,
            @PathVariable Long id) {

        mutationRateLimiter.assertAllowed(admin.getId());
        adminUserService.forceLogout(admin.getId(), id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private AdminUserListItemDto toRestDto(UserEntity user) {
        return adminUserRestMapper.toRestDto(adminUserMapper.toDto(user));
    }
}
