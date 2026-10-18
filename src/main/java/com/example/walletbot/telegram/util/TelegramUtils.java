package com.example.walletbot.telegram.util;

import java.net.SocketException;
import java.net.SocketTimeoutException;

public final class TelegramUtils {
    private TelegramUtils() {
    }

    public static boolean isTimeoutException(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof SocketException) return true;
        }
        return false;
    }
}