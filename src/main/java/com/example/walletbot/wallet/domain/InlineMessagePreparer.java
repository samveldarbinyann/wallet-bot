package com.example.walletbot.wallet.domain;

public interface InlineMessagePreparer {

    String preparePhotoMessage(Long telegramUserId, String photoUrl, String caption);
}
