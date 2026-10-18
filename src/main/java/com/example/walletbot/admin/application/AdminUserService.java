package com.example.walletbot.admin.application;

import com.example.walletbot.notification.application.NotificationService;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.shared.constant.UserRole;
import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.NotFoundException;
import com.example.walletbot.user.application.RefreshTokenService;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import com.example.walletbot.user.infrastructure.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final NotificationService notificationService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void ensureAdminRole(Long telegramId) {
        userRepository.findByTelegramId(telegramId).ifPresent(user -> {
            if (user.getRole() != UserRole.ADMIN) {
                user.setRole(UserRole.ADMIN);
                userRepository.save(user);
                userCacheService.evict(user.getId());
                log.info("Bootstrapped ADMIN role for telegramId={}", telegramId);
            }
        });
    }

    public Page<UserEntity> listUsers(UserStatus status, String q, Pageable pageable) {
        String pattern = (q != null && !q.isBlank()) ? "%" + q.trim().toLowerCase() + "%" : null;
        return userRepository.findAdminUsers(status, pattern, pageable);
    }

    public UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public UserEntity blockUser(Long adminId, Long targetUserId, String reason) {
        assertNotSelf(adminId, targetUserId);
        UserEntity user = getUser(targetUserId);
        user.setStatus(UserStatus.BLOCKED);
        user.setStatusReason(reason);
        UserEntity saved = saveAndEvict(adminId, targetUserId, user, true);
        notifyStatusChange(saved, NotificationType.ACCOUNT_BLOCKED, reason);
        return saved;
    }

    @Transactional
    public UserEntity freezeUser(Long adminId, Long targetUserId, String reason) {
        assertNotSelf(adminId, targetUserId);
        UserEntity user = getUser(targetUserId);
        user.setStatus(UserStatus.FROZEN);
        user.setStatusReason(reason);
        UserEntity saved = saveAndEvict(adminId, targetUserId, user, false);
        notifyStatusChange(saved, NotificationType.ACCOUNT_FROZEN, reason);
        return saved;
    }

    @Transactional
    public UserEntity unblockUser(Long adminId, Long targetUserId) {
        assertNotSelf(adminId, targetUserId);
        UserEntity user = getUser(targetUserId);
        UserStatus prevStatus = user.getStatus();
        user.setStatus(UserStatus.ACTIVE);
        user.setStatusReason(null);
        UserEntity saved = saveAndEvict(adminId, targetUserId, user, false);
        NotificationType type = prevStatus == UserStatus.BLOCKED
                ? NotificationType.ACCOUNT_UNBLOCKED
                : NotificationType.ACCOUNT_UNFROZEN;
        notifyStatusChange(saved, type, null);
        return saved;
    }

    @Transactional
    public void forceLogout(Long adminId, Long targetUserId) {
        assertNotSelf(adminId, targetUserId);
        UserEntity user = getUser(targetUserId);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenService.revokeAll(targetUserId);
        userCacheService.evict(targetUserId);
    }

    private UserEntity saveAndEvict(Long adminId, Long targetUserId, UserEntity user,
                                    boolean incrementTokenVersion) {
        user.setStatusChangedAt(Instant.now());
        user.setStatusChangedBy(adminId);
        if (incrementTokenVersion) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        UserEntity saved = userRepository.save(user);
        if (incrementTokenVersion) {
            refreshTokenService.revokeAll(targetUserId);
        }
        userCacheService.evict(targetUserId);
        return saved;
    }

    private void notifyStatusChange(UserEntity user, NotificationType type, String reason) {
        String reasonArg = reason == null ? "" : reason;
        String key = switch (type) {
            case ACCOUNT_FROZEN -> "notification.account.frozen";
            case ACCOUNT_UNFROZEN -> "notification.account.unfrozen";
            case ACCOUNT_BLOCKED -> "notification.account.blocked";
            case ACCOUNT_UNBLOCKED -> "notification.account.unblocked";
            default -> throw new IllegalArgumentException("Unsupported status notification: " + type);
        };
        notificationService.send(user.getId(), type, null, key + ".title", key + ".body", reasonArg);
    }

    private void assertNotSelf(Long adminId, Long targetUserId) {
        if (adminId.equals(targetUserId)) {
            throw new BadRequestException("error.admin_cannot_self_action");
        }
    }
}