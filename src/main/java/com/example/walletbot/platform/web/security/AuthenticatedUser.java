package com.example.walletbot.platform.web.security;

public record AuthenticatedUser(Long userId, Long telegramId, String role) {
}