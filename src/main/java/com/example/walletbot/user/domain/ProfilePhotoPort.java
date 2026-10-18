package com.example.walletbot.user.domain;

import java.util.Optional;

public interface ProfilePhotoPort {

    Optional<byte[]> fetchPhoto(long telegramId);
}
