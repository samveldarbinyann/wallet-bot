package com.example.walletbot.platform.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Converter
@Component
public class EncryptionConverter implements AttributeConverter<String, String> {

    private final TextEncryptor textEncryptor;

    @Autowired
    public EncryptionConverter(TextEncryptor textEncryptor) {
        this.textEncryptor = textEncryptor;
    }

    @Override
    public String convertToDatabaseColumn(String s) {
        return (s == null) ? null : textEncryptor.encrypt(s);
    }

    @Override
    public String convertToEntityAttribute(String s) {
        return (s == null) ? null : textEncryptor.decrypt(s);
    }
}