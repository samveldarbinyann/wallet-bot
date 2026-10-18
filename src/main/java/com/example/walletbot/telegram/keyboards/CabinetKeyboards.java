package com.example.walletbot.telegram.keyboards;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.shared.constant.UiEmoji;
import com.example.walletbot.user.domain.UserEntity;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

public final class CabinetKeyboards {

    private CabinetKeyboards() {
    }

    public static InlineKeyboardMarkup welcomeMenu(UserEntity userEntity, MessageService ms, String miniAppUrl) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        KeyboardSupport.webAppButton(userEntity, ms, "cabinet.open_app", miniAppUrl, UiEmoji.ASSETS)))
                .build();
    }

    public static InlineKeyboardMarkup backToWelcomeMenu(UserEntity userEntity, MessageService ms) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        KeyboardSupport.uiButton(userEntity, ms, "wallet.back", "cabinet:home", UiEmoji.BACK)))
                .build();
    }
}