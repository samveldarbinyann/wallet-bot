package com.example.walletbot.platform.web.advice;

import com.example.walletbot.shared.constant.SupportedLanguage;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestLocaleResolver {

    private RequestLocaleResolver() {
    }

    public static String getLang() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            if (sra.getRequest().getAttribute("userLang") instanceof String userLang) {
                SupportedLanguage fromUser = SupportedLanguage.fromTag(userLang);
                if (fromUser != null) return fromUser.name();
            }
            String header = sra.getRequest().getHeader("Accept-Language");
            if (header != null && !header.isBlank()) {
                SupportedLanguage fromHeader = SupportedLanguage.fromTag(header.split(",")[0]);
                if (fromHeader != null) return fromHeader.name();
            }
        }
        return SupportedLanguage.DEFAULT.name();
    }
}
