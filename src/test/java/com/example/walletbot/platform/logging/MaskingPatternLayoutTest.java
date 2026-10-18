package com.example.walletbot.platform.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingPatternLayoutTest {

    private final LoggerContext context = new LoggerContext();
    private final Logger logger = context.getLogger("test");

    private MaskingPatternLayout layout(String pattern) {
        MaskingPatternLayout layout = new MaskingPatternLayout();
        layout.setContext(context);
        layout.setPattern(pattern);
        layout.start();
        return layout;
    }

    @Test
    void masksKeyInRenderedMessage() {
        LoggingEvent event = new LoggingEvent(
                "fqcn", logger, Level.ERROR,
                "SOL RPC failed: https://mainnet.helius-rpc.com/?api-key=TOPSECRET", null, null);

        String rendered = layout("%msg").doLayout(event);

        assertThat(rendered).contains("api-key=***").doesNotContain("TOPSECRET");
    }

    @Test
    void masksKeyInRenderedStackTrace() {
        Throwable cause = new RuntimeException(
                "I/O error on POST request for \"https://eth-mainnet.g.alchemy.com/v2/ALCHEMYSECRET\"");
        LoggingEvent event = new LoggingEvent(
                "fqcn", logger, Level.ERROR, "broadcast failed", cause, null);

        String rendered = layout("%msg%n%ex").doLayout(event);

        assertThat(rendered).contains("/v2/***").doesNotContain("ALCHEMYSECRET");
        assertThat(rendered).contains("broadcast failed");
    }
}
