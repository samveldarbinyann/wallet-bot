package com.example.walletbot.notification.domain;

public interface PushNotificationSender {

    void send(Long chatId, String title, String body, String lang);

    void send(Long chatId, String title, String body, String lang, String explorerUrl);

    String sendPhoto(Long chatId, String title, String body, String lang, byte[] photo, String filename);

    void sendPhoto(Long chatId, String title, String body, String lang, String fileId);
}