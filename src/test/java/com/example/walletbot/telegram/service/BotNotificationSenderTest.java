package com.example.walletbot.telegram.service;

import com.example.walletbot.notification.application.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BotNotificationSenderTest {

    @Mock
    TelegramClient telegramClient;
    @Mock
    MessageService messageService;

    private BotNotificationSender sender;

    @BeforeEach
    void setUp() {
        when(messageService.getForButton(anyString(), anyString())).thenReturn("Open wallet");
        sender = new BotNotificationSender(telegramClient, messageService);
        ReflectionTestUtils.setField(sender, "miniAppUrl", "https://app.example.com");
    }

    @Test
    void send_escapesHtmlSpecialChars() throws TelegramApiException {
        sender.send(100L, "Fee < 1% & rising", "<script>alert(1)</script>", "en");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        String text = captor.getValue().getText();
        assertThat(text).doesNotContain("<script>");
        assertThat(text).contains("&lt;script&gt;");
        assertThat(text).contains("Fee &lt; 1% &amp; rising");
        assertThat(text).startsWith("<b>");
    }

    @Test
    void send_telegramFailure_isSwallowed() throws TelegramApiException {
        when(telegramClient.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiException("boom"));
        sender.send(100L, "t", "b", "en");
    }

    @Test
    void sendPhotoBytes_returnsUploadedFileId() throws TelegramApiException {
        PhotoSize size = mock(PhotoSize.class);
        when(size.getFileId()).thenReturn("FILE_ID_123");
        Message message = mock(Message.class);
        when(message.getPhoto()).thenReturn(List.of(size));
        when(telegramClient.execute(any(SendPhoto.class))).thenReturn(message);

        String fileId = sender.sendPhoto(100L, "t", "b", "en", new byte[]{1, 2, 3}, "x.png");

        assertThat(fileId).isEqualTo("FILE_ID_123");
    }

    @Test
    void sendPhotoBytes_telegramFailure_returnsNull() throws TelegramApiException {
        when(telegramClient.execute(any(SendPhoto.class)))
                .thenThrow(new TelegramApiException("boom"));
        assertThat(sender.sendPhoto(100L, "t", "b", "en", new byte[]{1}, "x.png")).isNull();
    }

    @Test
    void sendPhotoByFileId_executesSendPhoto() throws TelegramApiException {
        sender.sendPhoto(100L, "t", "b", "en", "CACHED_FILE_ID");
        verify(telegramClient).execute(any(SendPhoto.class));
    }
}