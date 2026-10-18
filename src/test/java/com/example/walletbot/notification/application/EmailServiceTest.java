package com.example.walletbot.notification.application;

import com.example.walletbot.notification.infrastructure.EmailTemplateRenderer;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    @Mock
    JavaMailSender mailSender;
    @Mock
    MessageService messageService;
    @Mock
    EmailTemplateRenderer templateRenderer;

    @InjectMocks
    EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "from", "noreply@example.com");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        when(messageService.get(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(templateRenderer.render(anyString(), any())).thenReturn("<html>body</html>");
    }

    @Test
    void sendOtp_rendersOtpTemplateAndSends() throws Exception {
        emailService.sendOtp("user@example.com", "123456", "en");

        verify(templateRenderer).render(eq("otp"), any());
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("user@example.com");
    }

    @Test
    void sendOtp_passesCodeIntoTemplateVariables() {
        emailService.sendOtp("user@example.com", "654321", "en");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(templateRenderer).render(eq("otp"), varsCaptor.capture());
        assertThat(varsCaptor.getValue()).containsEntry("code", "654321");
    }

    @Test
    void sendExportOtp_usesExportTemplate() {
        emailService.sendExportOtp("user@example.com", "111111", "en");
        verify(templateRenderer).render(eq("export-otp"), any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendNotification_usesNotificationTemplate() {
        emailService.sendNotification("user@example.com", "Subject", "Body", "en");
        verify(templateRenderer).render(eq("notification"), any());
        verify(mailSender).send(any(MimeMessage.class));
    }
}
