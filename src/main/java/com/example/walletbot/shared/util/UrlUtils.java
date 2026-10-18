package com.example.walletbot.shared.util;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String baseUrlOrDefault(String configured, String fallback) {
        return stripTrailingSlash(configured == null || configured.isBlank() ? fallback : configured);
    }
}
