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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    UserCacheService userCacheService;
    @Mock
    NotificationService notificationService;
    @Mock
    RefreshTokenService refreshTokenService;

    @InjectMocks
    AdminUserService service;

    private UserEntity target;

    @BeforeEach
    void setUp() {
        target = new UserEntity();
        target.setId(2L);
        target.setTelegramId(200L);
        target.setTokenVersion(0);
        target.setStatus(UserStatus.ACTIVE);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
    }

    @Test
    void ensureAdminRole_promotesNonAdmin() {
        target.setRole(UserRole.USER);
        when(userRepository.findByTelegramId(200L)).thenReturn(Optional.of(target));

        service.ensureAdminRole(200L);

        assertThat(target.getRole()).isEqualTo(UserRole.ADMIN);
        verify(userRepository).save(target);
        verify(userCacheService).evict(2L);
    }

    @Test
    void ensureAdminRole_alreadyAdmin_noChange() {
        target.setRole(UserRole.ADMIN);
        when(userRepository.findByTelegramId(200L)).thenReturn(Optional.of(target));

        service.ensureAdminRole(200L);

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUser_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void blockUser_self_throwsBadRequest() {
        assertThatThrownBy(() -> service.blockUser(2L, 2L, "reason"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.admin_cannot_self_action");
    }

    @Test
    void blockUser_setsBlockedIncrementsVersionRevokesTokensNotifies() {
        UserEntity saved = service.blockUser(1L, 2L, "fraud");

        assertThat(saved.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(saved.getStatusReason()).isEqualTo("fraud");
        assertThat(saved.getStatusChangedBy()).isEqualTo(1L);
        assertThat(saved.getStatusChangedAt()).isNotNull();
        assertThat(saved.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenService).revokeAll(2L);
        verify(userCacheService).evict(2L);
        verify(notificationService).send(eq(2L), eq(NotificationType.ACCOUNT_BLOCKED), any(), any(), any(), eq("fraud"));
    }

    @Test
    void freezeUser_setsFrozenWithoutTokenBump() {
        UserEntity saved = service.freezeUser(1L, 2L, "review");

        assertThat(saved.getStatus()).isEqualTo(UserStatus.FROZEN);
        assertThat(saved.getTokenVersion()).isEqualTo(0);
        verify(refreshTokenService, never()).revokeAll(any());
        verify(notificationService).send(eq(2L), eq(NotificationType.ACCOUNT_FROZEN), any(), any(), any(), eq("review"));
    }

    @Test
    void unblockUser_fromBlocked_sendsUnblockedNotification() {
        target.setStatus(UserStatus.BLOCKED);

        UserEntity saved = service.unblockUser(1L, 2L);

        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getStatusReason()).isNull();
        verify(notificationService).send(eq(2L), eq(NotificationType.ACCOUNT_UNBLOCKED), any(), any(), any(), eq(""));
    }

    @Test
    void unblockUser_fromFrozen_sendsUnfrozenNotification() {
        target.setStatus(UserStatus.FROZEN);

        service.unblockUser(1L, 2L);

        verify(notificationService).send(eq(2L), eq(NotificationType.ACCOUNT_UNFROZEN), any(), any(), any(), eq(""));
    }

    @Test
    void forceLogout_bumpsVersionAndRevokesTokens() {
        service.forceLogout(1L, 2L);

        assertThat(target.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenService).revokeAll(2L);
        verify(userCacheService).evict(2L);
    }
}
