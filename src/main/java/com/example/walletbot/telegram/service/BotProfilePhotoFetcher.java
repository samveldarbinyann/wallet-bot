package com.example.walletbot.telegram.service;

import com.example.walletbot.user.domain.ProfilePhotoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.GetUserProfilePhotos;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotProfilePhotoFetcher implements ProfilePhotoPort {

    private final TelegramClient telegramClient;

    @Override
    @Cacheable("profile-photo")
    public Optional<byte[]> fetchPhoto(long telegramId) {
        try {
            var photos = telegramClient.execute(GetUserProfilePhotos.builder()
                    .userId(telegramId)
                    .limit(1)
                    .build());
            if (photos == null || photos.getPhotos() == null || photos.getPhotos().isEmpty()) {
                return Optional.empty();
            }
            List<PhotoSize> sizes = photos.getPhotos().get(0);
            Optional<PhotoSize> smallest = sizes.stream()
                    .min(Comparator.comparingInt(PhotoSize::getWidth));
            if (smallest.isEmpty()) {
                return Optional.empty();
            }
            File file = telegramClient.execute(GetFile.builder().fileId(smallest.get().getFileId()).build());
            try (InputStream in = telegramClient.downloadFileAsStream(file)) {
                return Optional.of(in.readAllBytes());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Telegram profile photo for telegramId {}: {}", telegramId, e.getMessage());
            return Optional.empty();
        }
    }
}
