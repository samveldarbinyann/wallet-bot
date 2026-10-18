package com.example.walletbot.platform.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EncryptionConverterTest {

    private TextEncryptor encryptor;
    private EncryptionConverter converter;

    @BeforeEach
    void setUp() {
        encryptor = mock(TextEncryptor.class);
        converter = new EncryptionConverter(encryptor);
    }

    @Test
    void convertToDatabaseColumn_encryptsNonNull() {
        when(encryptor.encrypt("secret")).thenReturn("ENC");
        assertThat(converter.convertToDatabaseColumn("secret")).isEqualTo("ENC");
    }

    @Test
    void convertToEntityAttribute_decryptsNonNull() {
        when(encryptor.decrypt("ENC")).thenReturn("secret");
        assertThat(converter.convertToEntityAttribute("ENC")).isEqualTo("secret");
    }

    @Test
    void convertToDatabaseColumn_null_passthroughWithoutEncrypting() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        verifyNoInteractions(encryptor);
    }

    @Test
    void convertToEntityAttribute_null_passthroughWithoutDecrypting() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        verifyNoInteractions(encryptor);
    }
}
