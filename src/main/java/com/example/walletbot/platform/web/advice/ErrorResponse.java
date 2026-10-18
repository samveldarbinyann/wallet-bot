package com.example.walletbot.platform.web.advice;

import java.util.Map;

public record ErrorResponse(
        String message,
        String code,
        int status,
        Map<String, String> fieldErrors,
        String reason
) {
    public ErrorResponse(String message, String code, int status, Map<String, String> fieldErrors) {
        this(message, code, status, fieldErrors, null);
    }
}
