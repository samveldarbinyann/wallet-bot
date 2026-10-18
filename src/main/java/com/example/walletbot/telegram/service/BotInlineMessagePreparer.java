package com.example.walletbot.telegram.service;

import com.example.walletbot.wallet.domain.InlineMessagePreparer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.message.SavePreparedInlineMessage;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultPhoto;
import org.telegram.telegrambots.meta.api.objects.message.PreparedInlineMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotInlineMessagePreparer implements InlineMessagePreparer {

    private final TelegramClient telegramClient;

    @Override
    public String preparePhotoMessage(Long telegramUserId, String photoUrl, String caption) {
        try {
            InlineQueryResultPhoto photo = InlineQueryResultPhoto.builder()
                    .id(UUID.randomUUID().toString())
                    .photoUrl(photoUrl)
                    .thumbnailUrl(photoUrl)
                    .mimeType("image/jpeg")
                    .caption(caption)
                    .build();
            SavePreparedInlineMessage method = SavePreparedInlineMessage.builder()
                    .userId(telegramUserId)
                    .result(photo)
                    .allowUserChats(true)
                    .allowBotChats(true)
                    .allowGroupChats(true)
                    .allowChannelChats(true)
                    .build();
            PreparedInlineMessage prepared = telegramClient.execute(method);
            return prepared != null ? prepared.getMessageId() : null;
        } catch (Exception e) {
            log.warn("savePreparedInlineMessage failed for userId {}: {}", telegramUserId, e.getMessage());
            return null;
        }
    }
}
