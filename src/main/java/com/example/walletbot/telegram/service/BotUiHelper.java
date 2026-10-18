package com.example.walletbot.telegram.service;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.telegram.tracker.BotMessageTracker;
import com.example.walletbot.user.domain.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static com.example.walletbot.telegram.util.TelegramUtils.isTimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotUiHelper {

    private final TelegramClient telegramClient;
    private final BotMessageTracker messageTracker;
    private final MessageService messageService;

    public String m(UserEntity userEntity, String key, Object... args) {
        return messageService.get(userEntity.getLang(), key, args);
    }

    public void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        var builder = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true);

        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }

        EditMessageText request = builder.build();

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                telegramClient.execute(request);
                messageTracker.track(chatId, messageId);
                return;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("message is not modified")) {
                    messageTracker.track(chatId, messageId);
                    return;
                } else if (msg.contains("no text in the message")) {
                    sendNewMessage(chatId, text, keyboard);
                    deleteMessage(chatId, messageId);
                    return;
                } else if (msg.contains("message to edit not found")) {
                    return;
                } else if (isTimeoutException(e) && attempt < 2) {
                    log.warn("editMessage timeout, retrying (chatId={}, msgId={})", chatId, messageId);
                } else {
                    log.error("editMessage error", e);
                    sendNewMessage(chatId, text, keyboard);
                    deleteMessage(chatId, messageId);
                    return;
                }
            }
        }
    }


    public void deleteMessage(Long chatId, Integer messageId) {
        try {
            telegramClient.execute(DeleteMessage.builder()
                    .chatId(chatId.toString()).messageId(messageId).build());
        } catch (Exception e) {
            log.debug("Failed to delete message {}", messageId);
        }
    }

    public void sendNewMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            var result = telegramClient.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .replyMarkup(keyboard)
                    .build());
            if (result instanceof Message msg) {
                messageTracker.track(chatId, msg.getMessageId());
            }
        } catch (Exception e) {
            log.error("Failed to send message", e);
        }
    }

    private String greetingDisplayName(UserEntity userEntity) {
        String name = userEntity.getUsername();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return messageService.get(userEntity.getLang(), "common.greeting_name_fallback");
    }

    public void replaceLastBotMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        Integer oldMsgId = messageTracker.getLast(chatId);
        sendNewMessage(chatId, text, keyboard);
        if (oldMsgId != null) {
            deleteMessage(chatId, oldMsgId);
        }
    }

    public String buildWelcomeText(UserEntity userEntity) {
        return messageService.get(userEntity.getLang(), "info.text", greetingDisplayName(userEntity));
    }

}
