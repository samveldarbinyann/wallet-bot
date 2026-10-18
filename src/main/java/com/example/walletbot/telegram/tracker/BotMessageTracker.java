package com.example.walletbot.telegram.tracker;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BotMessageTracker {

    private final Cache<Long, Integer> lastMessage = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(7, TimeUnit.DAYS)
            .build();

    public void track(Long chatId, Integer messageId) {
        if (chatId != null && messageId != null) {
            lastMessage.put(chatId, messageId);
        }
    }

    public Integer getLast(Long chatId) {
        return lastMessage.getIfPresent(chatId);
    }
}