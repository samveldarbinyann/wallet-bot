package com.example.walletbot.telegram.keyboards;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.shared.constant.UiEmoji;
import com.example.walletbot.user.domain.UserEntity;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

final class KeyboardSupport {

    private KeyboardSupport() {
    }

    static InlineKeyboardButton uiButton(UserEntity user, MessageService ms, String key, String callback, UiEmoji emoji) {
        String text = ms.getForButton(user.getLang(), key);
        return button(text, callback, emoji.getCustomEmojiIdOrNull());
    }

    static InlineKeyboardButton webAppButton(UserEntity user, MessageService ms, String key, String url, UiEmoji emoji) {
        String text = ms.getForButton(user.getLang(), key);
        var builder = InlineKeyboardButton.builder()
                .text(text)
                .webApp(WebAppInfo.builder().url(url).build());
        String iconId = emoji.getCustomEmojiIdOrNull();
        if (iconId != null && !iconId.isEmpty()) {
            builder.iconCustomEmojiId(iconId);
        }
        return builder.build();
    }

    static InlineKeyboardButton button(String text, String callbackData, String iconCustomEmojiId) {
        var builder = InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData);
        if (iconCustomEmojiId != null && !iconCustomEmojiId.isEmpty()) {
            builder.iconCustomEmojiId(iconCustomEmojiId);
        }
        return builder.build();
    }
}
