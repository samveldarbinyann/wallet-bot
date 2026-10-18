package com.example.walletbot.admin.infrastructure;

import com.example.walletbot.admin.application.AdminUserService;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminUserService adminUserService;

    @Value("${admin.bootstrap.telegram-ids:}")
    private List<Long> adminTelegramIds;

    @Override
    public void run(@Nonnull ApplicationArguments args) {
        if (adminTelegramIds == null || adminTelegramIds.isEmpty()) return;
        adminTelegramIds.forEach(adminUserService::ensureAdminRole);
    }
}