package com.example.walletbot.telegram.handler.callback;

import com.example.walletbot.user.domain.UserEntity;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

public interface CallbackHandler {

    boolean canHandle(String callbackData);

    void handle(CallbackQuery callback, UserEntity userEntity, Long chatId, Integer messageId);
}
