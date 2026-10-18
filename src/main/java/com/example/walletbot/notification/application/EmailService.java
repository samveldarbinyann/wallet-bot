package com.example.walletbot.notification.application;

import com.example.walletbot.notification.infrastructure.EmailTemplateRenderer;
import com.example.walletbot.shared.util.EmailMasker;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final MessageService messageService;
    private final EmailTemplateRenderer templateRenderer;

    @Value("${spring.mail.username}")
    private String from;

    public void sendOtp(String to, String code, String lang) {
        String subject = messageService.get(lang, "email.otp.subject");
        Map<String, Object> vars = Map.of(
                "title", messageService.get(lang, "email.otp.title"),
                "intro", messageService.get(lang, "email.otp.intro"),
                "code", code,
                "expiry", messageService.get(lang, "email.common.expiry"),
                "security", messageService.get(lang, "email.common.security"),
                "footer", messageService.get(lang, "email.common.footer")
        );
        sendHtml(to, subject, templateRenderer.render("otp", vars));
    }

    public void sendExportOtp(String to, String code, String lang) {
        String subject = messageService.get(lang, "email.export.subject");
        Map<String, Object> vars = Map.of(
                "title", messageService.get(lang, "email.export.title"),
                "warning", messageService.get(lang, "email.export.warning"),
                "warningText", messageService.get(lang, "email.export.warningText"),
                "intro", messageService.get(lang, "email.export.intro"),
                "code", code,
                "expiry", messageService.get(lang, "email.common.expiry"),
                "security", messageService.get(lang, "email.export.securityNote"),
                "footer", messageService.get(lang, "email.common.footer")
        );
        sendHtml(to, subject, templateRenderer.render("export-otp", vars));
    }

    public void sendNotification(String to, String subject, String body, String lang) {
        Map<String, Object> vars = Map.of(
                "title", subject,
                "body", body,
                "footer", messageService.get(lang, "email.common.footer")
        );
        sendHtml(to, subject, templateRenderer.render("notification", vars));
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", EmailMasker.mask(to), e.getMessage());
            throw new RuntimeException("Email send failed", e);
        }
    }
}
