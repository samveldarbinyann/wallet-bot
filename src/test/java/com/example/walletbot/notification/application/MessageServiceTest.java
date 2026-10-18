package com.example.walletbot.notification.application;

import com.example.walletbot.shared.constant.UiEmoji;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceTest {

    private MessageService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new MessageService();
        Properties en = new Properties();
        en.setProperty("greeting", "Hello {0} and {1}");
        en.setProperty("multiline", "line1\\nline2");
        en.setProperty("ui.emoji", "{e:ASSETS} Assets");
        en.setProperty("ui.unknown_emoji", "{e:NOPE} x");
        Properties ru = new Properties();
        ru.setProperty("greeting", "Привет {0}");

        Map<String, Properties> locales =
                (Map<String, Properties>) ReflectionTestUtils.getField(service, "locales");
        locales.put("en", en);
        locales.put("ru", ru);
    }

    @Test
    void get_substitutesPositionalArgs() {
        assertThat(service.get("en", "greeting", "Sam", "Bob")).isEqualTo("Hello Sam and Bob");
    }

    @Test
    void get_unknownKey_returnsKey() {
        assertThat(service.get("en", "missing.key")).isEqualTo("missing.key");
    }

    @Test
    void get_unknownLang_fallsBackToEnglish() {
        assertThat(service.get("fr", "greeting", "Sam", "Bob")).isEqualTo("Hello Sam and Bob");
    }

    @Test
    void get_usesRequestedLangWhenAvailable() {
        assertThat(service.get("ru", "greeting", "Сэм")).isEqualTo("Привет Сэм");
    }

    @Test
    void get_translatesLiteralNewline() {
        assertThat(service.get("en", "multiline")).isEqualTo("line1\nline2");
    }

    @Test
    void get_resolvesEmojiPlaceholderForMessage() {
        assertThat(service.get("en", "ui.emoji")).contains(UiEmoji.ASSETS.getForMessage());
    }

    @Test
    void getForButton_resolvesEmojiForButton() {
        assertThat(service.getForButton("en", "ui.emoji")).contains(UiEmoji.ASSETS.getForButton());
    }

    @Test
    void get_unknownEmojiPlaceholder_keptLiteral() {
        assertThat(service.get("en", "ui.unknown_emoji")).isEqualTo("{e:NOPE} x");
    }

    @Test
    void getAllUiMessages_returnsPrefixedKeysOnly() {
        Map<String, String> all = service.getAllUiMessages("en");

        assertThat(all).containsKey("ui.emoji");
        assertThat(all).doesNotContainKey("greeting");
    }
}
