package com.example.walletbot.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mail.twofa")
public record MailProperties(
        int otpTtlMinutes,
        int resendCooldownSeconds,
        int maxAttempts
) {
}