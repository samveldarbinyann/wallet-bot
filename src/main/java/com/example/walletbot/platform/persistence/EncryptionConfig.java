package com.example.walletbot.platform.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration
public class EncryptionConfig {

    private static final int MIN_PASSWORD_LENGTH = 24;
    private static final int MIN_SALT_HEX_LENGTH = 16;

    @Value("${encryption.password}")
    private String password;
    @Value("${encryption.salt}")
    private String salt;

    @Bean
    public TextEncryptor textEncryptor() {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "encryption.password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (salt == null || salt.length() < MIN_SALT_HEX_LENGTH || !salt.matches("[0-9a-fA-F]+")) {
            throw new IllegalStateException(
                    "encryption.salt must be a hex string of at least " + MIN_SALT_HEX_LENGTH + " characters");
        }
        return Encryptors.text(password, salt);
    }
}
