package com.example.walletbot.notification.application;

import com.example.walletbot.shared.constant.SupportedLanguage;
import com.example.walletbot.shared.constant.UiEmoji;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MessageService {

    private static final Pattern EMOJI_PLACEHOLDER = Pattern.compile("\\{e:([A-Z_]+)}");

    private final Map<String, Properties> locales = new HashMap<>();

    private final Map<String, Map<String, String>> uiMessageCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws Exception {
        for (SupportedLanguage lang : SupportedLanguage.values()) {
            locales.put(lang.name(), load("i18n/messages_" + lang.name() + ".properties"));
        }
    }

    private Properties load(String filename) throws Exception {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (is == null) {
                throw new IllegalStateException("Localization file not found: " + filename);
            }
            props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
        }
        return props;
    }

    public String get(String lang, String key, Object... args) {
        return resolve(lang, key, false, args);
    }

    public String getForButton(String lang, String key, Object... args) {
        return resolve(lang, key, true, args);
    }

    public boolean has(String lang, String key) {
        return locales.getOrDefault(lang, locales.get(SupportedLanguage.DEFAULT.name()))
                .containsKey(key);
    }

    public Map<String, String> getAllUiMessages(String lang) {
        String code = locales.containsKey(lang) ? lang : SupportedLanguage.DEFAULT.name();
        return uiMessageCache.computeIfAbsent(code, this::buildUiMessages);
    }

    private Map<String, String> buildUiMessages(String lang) {
        Properties props = locales.get(lang);
        Map<String, String> result = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.endsWith(".bot")) {
                continue;
            }
            if (key.startsWith("ui.") || key.startsWith("admin.") || key.startsWith("error.") || key.startsWith("common.") || key.startsWith("notification.")) {
                result.put(key, resolve(lang, key, true));
            }
        }
        return Map.copyOf(result);
    }

    private String resolve(String lang, String key, boolean forButton, Object... args) {
        String template = locales
                .getOrDefault(lang, locales.get(SupportedLanguage.DEFAULT.name()))
                .getProperty(key, key);
        template = resolveEmojiPlaceholders(template, forButton);
        for (int i = 0; i < args.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return template.replace("\\n", "\n");
    }

    private String resolveEmojiPlaceholders(String template, boolean forButton) {
        Matcher m = EMOJI_PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            try {
                UiEmoji emoji = UiEmoji.valueOf(m.group(1));
                String replacement = forButton ? emoji.getForButton() : emoji.getForMessage();
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } catch (IllegalArgumentException ignored) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
