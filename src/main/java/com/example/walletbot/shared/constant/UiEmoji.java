package com.example.walletbot.shared.constant;

import lombok.Getter;


@Getter
public enum UiEmoji {
    ASSETS("💼", "5352861266203414978"),
    SETTINGS("⚙️", "5350573805276273524"),
    LANGUAGE("🌐", "5208942852054204115"),
    CURRENCY("💱", "5208886905810208630"),
    TIMEZONE("🕐", "5208510215703512539"),
    BACK("🔙", "5352791202401918166"),

    SUCCESS("✅", "5208763618773978162"),
    LOCK("🔐", "5208655754965304255"),

    ASSET_BTC("🟠", "5242625806655570503"),
    ASSET_ETH("🔷", "5246838478083211008"),
    ASSET_BNB("🟡", "5249269678680852956"),
    ASSET_SOL("🟢", "5240212838194104761"),
    ASSET_TON("💎", "5239983985156703956"),
    ASSET_TRX("🔴", "5240137306899235534"),
    ASSET_USDT("💵", "5242551409232069476"),
    ASSET_USDC("💲", "5240086656349913841");

    private final String fallback;
    private final String customEmojiId;

    UiEmoji(String fallback, String customEmojiId) {
        this.fallback = fallback;
        this.customEmojiId = customEmojiId;
    }

    public String getForMessage() {
        if (customEmojiId != null && !customEmojiId.isEmpty()) {
            return "<tg-emoji emoji-id=\"" + customEmojiId + "\">" + fallback + "</tg-emoji>";
        }
        return fallback;
    }

    public String getForButton() {
        return customEmojiId != null && !customEmojiId.isEmpty() ? "" : fallback;
    }

    public String getCustomEmojiIdOrNull() {
        return (customEmojiId != null && !customEmojiId.isEmpty()) ? customEmojiId : null;
    }
}