package com.example.walletbot.notification.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Component
public class EmailTemplateRenderer {

    private final TemplateEngine emailTemplateEngine;

    public EmailTemplateRenderer(@Qualifier("emailTemplateEngine") TemplateEngine emailTemplateEngine) {
        this.emailTemplateEngine = emailTemplateEngine;
    }

    public String render(String template, Map<String, Object> variables) {
        Context ctx = new Context();
        ctx.setVariables(variables);
        return emailTemplateEngine.process(template, ctx);
    }
}
