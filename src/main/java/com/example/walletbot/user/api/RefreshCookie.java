package com.example.walletbot.user.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookie {

    public static final String NAME = "refresh";
    private static final String PATH = "/api/miniapp/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookie(@Value("${app.cookie-secure:true}") boolean secure,
                         @Value("${jwt.refresh-ttl-days:30}") long refreshTtlDays) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(refreshTtlDays);
    }

    public void set(HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(rawToken, maxAge).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(age)
                .build();
    }
}
