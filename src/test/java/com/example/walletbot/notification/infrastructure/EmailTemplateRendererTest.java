package com.example.walletbot.notification.infrastructure;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmailTemplateRendererTest {

    @Test
    void render_passesTemplateAndVariablesToEngine() {
        TemplateEngine engine = mock(TemplateEngine.class);
        when(engine.process(eq("otp-email"), any(IContext.class))).thenReturn("<html>123456</html>");
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(engine);

        String html = renderer.render("otp-email", Map.of("code", "123456", "lang", "en"));

        assertThat(html).isEqualTo("<html>123456</html>");

        ArgumentCaptor<IContext> ctxCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(engine).process(eq("otp-email"), ctxCaptor.capture());
        Context ctx = (Context) ctxCaptor.getValue();
        assertThat(ctx.getVariable("code")).isEqualTo("123456");
        assertThat(ctx.getVariable("lang")).isEqualTo("en");
    }
}
