package com.example.walletbot.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService();

    @Test
    void generateQrCode_validContent_returnsPngBytes() {
        byte[] png = service.generateQrCode("ethereum:0xabc?amount=1");

        assertThat(png).isNotNull();
        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }

    @Test
    void generateQrCode_emptyContent_returnsNull() {
        assertThat(service.generateQrCode("")).isNull();
    }

    @Test
    void generateQrDataUrl_validContent_returnsBase64DataUrl() {
        String url = service.generateQrDataUrl("hello");

        assertThat(url).isNotNull().startsWith("data:image/png;base64,");
    }

    @Test
    void generateQrDataUrl_emptyContent_returnsNull() {
        assertThat(service.generateQrDataUrl("")).isNull();
    }
}