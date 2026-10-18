package com.example.walletbot.user.application;

import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.shared.exception.AccountBlockedException;
import com.example.walletbot.shared.exception.AccountFrozenException;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserStatusGuardTest {

    private final UserStatusGuard guard = new UserStatusGuard();

    private UserEntity userWithStatus(UserStatus status) {
        UserEntity u = new UserEntity();
        u.setStatus(status);
        u.setStatusReason("reason-x");
        return u;
    }

    @Test
    void requireCanTransact_active_passes() {
        assertThatCode(() -> guard.requireCanTransact(userWithStatus(UserStatus.ACTIVE)))
                .doesNotThrowAnyException();
    }

    @Test
    void requireCanTransact_blocked_throwsAccountBlocked() {
        assertThatThrownBy(() -> guard.requireCanTransact(userWithStatus(UserStatus.BLOCKED)))
                .isInstanceOf(AccountBlockedException.class);
    }

    @Test
    void requireCanTransact_frozen_throwsAccountFrozen() {
        assertThatThrownBy(() -> guard.requireCanTransact(userWithStatus(UserStatus.FROZEN)))
                .isInstanceOf(AccountFrozenException.class);
    }
}
