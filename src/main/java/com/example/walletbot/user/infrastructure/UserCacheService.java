package com.example.walletbot.user.infrastructure;

import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserCacheService {
    private final UserRepository userRepository;

    @Cacheable(value = "users", key = "#userId")
    public Optional<UserEntity> findById(Long userId) {
        return userRepository.findById(userId);
    }

    @CacheEvict(value = "users", key = "#userId")
    public void evict(@SuppressWarnings("unused") Long userId) {
    }
}