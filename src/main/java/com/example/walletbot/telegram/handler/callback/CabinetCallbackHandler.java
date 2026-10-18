package com.example.walletbot.telegram.handler.callback;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.telegram.keyboards.CabinetKeyboards;
import com.example.walletbot.telegram.service.BotUiHelper;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Slf4j
@Component
@Order(1)
public class CabinetCallbackHandler implements CallbackHandler {

    private final WalletService walletService;
    private final MessageService messageService;
    private final BotUiHelper uiHelper;
    private final String miniAppUrl;

    public CabinetCallbackHandler(
            WalletService walletService,
            MessageService messageService,
            BotUiHelper uiHelper,
            @Value("${telegram.miniapp.url}") String miniAppUrl) {
        this.walletService = walletService;
        this.messageService = messageService;
        this.uiHelper = uiHelper;
        this.miniAppUrl = miniAppUrl;
    }

    @Override
    public boolean canHandle(String callbackData) {
        return "cabinet:home".equals(callbackData) || "cabinet".equals(callbackData);
    }

    @Override
    public void handle(CallbackQuery callback, UserEntity userEntity, Long chatId, Integer messageId) {
        if (walletService.needsWalletsForUser(userEntity.getId())) {
            walletService.createAllWallets(userEntity);
        }

        uiHelper.editMessage(chatId, messageId,
                uiHelper.buildWelcomeText(userEntity),
                CabinetKeyboards.welcomeMenu(userEntity, messageService, miniAppUrl));
    }
}