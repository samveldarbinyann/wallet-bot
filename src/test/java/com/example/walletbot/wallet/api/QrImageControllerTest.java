package com.example.walletbot.wallet.api;

import com.example.walletbot.shared.util.QrCodeService;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.infrastructure.QrShareTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QrImageControllerTest {

    private QrShareTokenStore tokenStore;
    private QrCodeService qrCodeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        tokenStore = mock(QrShareTokenStore.class);
        qrCodeService = mock(QrCodeService.class);
        mvc = MiniAppMvc.forController(new QrImageController(tokenStore, qrCodeService), new UserEntity());
    }

    @Test
    void getQr_validToken_returnsJpeg() throws Exception {
        when(tokenStore.resolve("tok")).thenReturn("0xABC");
        when(qrCodeService.generateQrCodeJpeg("0xABC")).thenReturn(new byte[]{1, 2, 3});

        mvc.perform(get("/api/files/qr/tok"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));
    }

    @Test
    void getQr_unknownToken_returns404() throws Exception {
        when(tokenStore.resolve("tok")).thenReturn(null);

        mvc.perform(get("/api/files/qr/tok")).andExpect(status().isNotFound());
    }

    @Test
    void getQr_jpegGenerationFails_returns404() throws Exception {
        when(tokenStore.resolve("tok")).thenReturn("0xABC");
        when(qrCodeService.generateQrCodeJpeg("0xABC")).thenReturn(null);

        mvc.perform(get("/api/files/qr/tok")).andExpect(status().isNotFound());
    }
}
