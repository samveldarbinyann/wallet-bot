package com.example.walletbot.telegram;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.telegram.handler.callback.CallbackRouter;
import com.example.walletbot.telegram.keyboards.CabinetKeyboards;
import com.example.walletbot.telegram.service.BotUiHelper;
import com.example.walletbot.user.application.UserService;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;


@Slf4j
@Component
@RequiredArgsConstructor
public class Bot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final UserService userService;
    private final WalletService walletService;
    private final CallbackRouter callbackRouter;
    private final BotUiHelper uiHelper;
    private final MessageService messageService;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.miniapp.url}")
    private String miniAppUrl;

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                callbackRouter.route(update.getCallbackQuery());
                return;
            }

            if (!update.hasMessage() || !update.getMessage().hasText())
                return;

            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText().trim();
            Integer userMsgId = update.getMessage().getMessageId();
            var from = update.getMessage().getFrom();
            UserEntity userEntity = userService.getOrCreateUser(
                    chatId, from.getUserName(), null, null, from.getLanguageCode());

            if ("/start".equals(text)) {
                uiHelper.deleteMessage(chatId, userMsgId);
                if (walletService.needsWalletsForUser(userEntity.getId())) {
                    walletService.createAllWallets(userEntity);
                }

                uiHelper.replaceLastBotMessage(chatId,
                        uiHelper.buildWelcomeText(userEntity),
                        CabinetKeyboards.welcomeMenu(userEntity, messageService, miniAppUrl));
                return;
            }

            uiHelper.deleteMessage(chatId, userMsgId);

        } catch (Exception e) {
            log.error("Update processing error", e);
        }
    }

}