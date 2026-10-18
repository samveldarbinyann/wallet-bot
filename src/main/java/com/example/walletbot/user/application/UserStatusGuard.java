package com.example.walletbot.user.application;

import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.shared.exception.AccountBlockedException;
import com.example.walletbot.shared.exception.AccountFrozenException;
import com.example.walletbot.user.domain.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserStatusGuard {

    public void requireNotBlocked(UserEntity user) {
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AccountBlockedException(user.getStatusReason());
        }
    }

    public void requireCanTransact(UserEntity user) {
        requireNotBlocked(user);
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new AccountFrozenException(user.getStatusReason());
        }
    }
}