package com.example.walletbot.telegram.service;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.notification.domain.PushNotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotNotificationSender implements PushNotificationSender {

    private static final int MESSAGE_LIMIT = 4096;
    private static final int CAPTION_LIMIT = 1024;

    private final TelegramClient telegramClient;
    private final MessageService messageService;

    @Value("${telegram.miniapp.url:http://localhost:3000}")
    private String miniAppUrl;

    private static String htmlMessage(String title, String body, int limit) {
        String text = "<b>" + escapeHtml(title) + "</b>\n\n" + escapeHtml(body);
        if (text.length() <= limit) {
            return text;
        }
        String cut = text.substring(0, limit);
        int lastAmp = cut.lastIndexOf('&');
        if (lastAmp >= 0 && cut.indexOf(';', lastAmp) < 0) {
            cut = cut.substring(0, lastAmp);
        }
        return cut;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    @Override
    public void send(Long telegramChatId, String title, String body, String lang) {
        send(telegramChatId, title, body, lang, null);
    }

    @Override
    public void send(Long telegramChatId, String title, String body, String lang, String explorerUrl) {
        try {
            String text = htmlMessage(title, body, MESSAGE_LIMIT);
            if (explorerUrl != null && !explorerUrl.isBlank()) {
                text += "\n<a href=\"" + escapeHtml(explorerUrl) + "\">Explorer</a>";
            }
            telegramClient.execute(SendMessage.builder()
                    .chatId(telegramChatId.toString())
                    .text(text)
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .replyMarkup(openWalletKeyboard(lang))
                    .build());
        } catch (Exception e) {
            log.warn("Telegram notification failed for chatId {}: {}", telegramChatId, e.getMessage());
        }
    }

    @Override
    public String sendPhoto(Long telegramChatId, String title, String body, String lang, byte[] photo, String filename) {
        Message msg = executePhoto(telegramChatId,
                new InputFile(new ByteArrayInputStream(photo), filename), title, body, lang);
        if (msg != null && msg.getPhoto() != null && !msg.getPhoto().isEmpty()) {
            return msg.getPhoto().get(msg.getPhoto().size() - 1).getFileId();
        }
        return null;
    }

    @Override
    public void sendPhoto(Long telegramChatId, String title, String body, String lang, String fileId) {
        executePhoto(telegramChatId, new InputFile(fileId), title, body, lang);
    }

    private Message executePhoto(Long telegramChatId, InputFile photo, String title, String body, String lang) {
        try {
            return telegramClient.execute(SendPhoto.builder()
                    .chatId(telegramChatId.toString())
                    .photo(photo)
                    .caption(htmlMessage(title, body, CAPTION_LIMIT))
                    .parseMode("HTML")
                    .replyMarkup(openWalletKeyboard(lang))
                    .build());
        } catch (Exception e) {
            log.warn("Telegram photo notification failed for chatId {}: {}", telegramChatId, e.getMessage());
            return null;
        }
    }

    private InlineKeyboardMarkup openWalletKeyboard(String lang) {
        String openLabel = messageService.getForButton(lang, "cabinet.open_app");
        var openWalletButton = InlineKeyboardButton.builder()
                .text(openLabel)
                .webApp(new WebAppInfo(miniAppUrl))
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(openWalletButton))
                .build();
    }
}