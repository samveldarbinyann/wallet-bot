package com.example.walletbot.telegram;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class BotConfig {

    @Bean
    public TelegramClient telegramClient(@Value("${telegram.bot.token}") String botToken) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(5, 30, TimeUnit.SECONDS))
                .build();
        return new OkHttpTelegramClient(httpClient, botToken);
    }
}