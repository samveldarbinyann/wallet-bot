package com.example.walletbot.telegram.handler.callback;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.telegram.keyboards.CabinetKeyboards;
import com.example.walletbot.telegram.service.BotUiHelper;
import com.example.walletbot.user.application.UserService;
import com.example.walletbot.user.domain.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackRouter {

    private final TelegramClient telegramClient;
    private final UserService userService;
    private final MessageService messageService;
    private final BotUiHelper uiHelper;
    private final List<CallbackHandler> handlers;

    public void route(CallbackQuery callback) {
        if (callback.getMessage() == null) {
            log.warn("Callback without message (inline mode): {}", callback.getData());
            return;
        }
        if (callback.getData() == null || callback.getData().isBlank()) {
            log.warn("Empty callback data from chatId={}", callback.getMessage().getChatId());
            return;
        }
        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();
        UserEntity userEntity = userService.getOrCreateUser(
                chatId, callback.getFrom().getUserName(), null, null, callback.getFrom().getLanguageCode());

        log.info("Callback: {} from {}", data, chatId);

        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callback.getId()).build());
            for (CallbackHandler handler : handlers) {
                if (handler.canHandle(data)) {
                    handler.handle(callback, userEntity, chatId, messageId);
                    return;
                }
            }

            log.warn("Unknown callback: {}", data);

        } catch (Exception e) {
            log.error("Callback processing error {}", data, e);
            String errorText = "⚠️ " + uiHelper.m(userEntity, "error.unexpected");
            var errorKeyboard = CabinetKeyboards.backToWelcomeMenu(userEntity, messageService);
            try {
                uiHelper.editMessage(chatId, messageId, errorText, errorKeyboard);
            } catch (Exception ignored) {
                try {
                    telegramClient.execute(SendMessage.builder()
                            .chatId(chatId.toString())
                            .text(errorText)
                            .parseMode("HTML")
                            .replyMarkup(errorKeyboard)
                            .build());
                } catch (Exception ignored2) {
                }
            }
        }
    }

}
