package com.example.walletbot.wallet.api;

import com.example.walletbot.shared.util.QrCodeService;
import com.example.walletbot.wallet.infrastructure.QrShareTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/files/qr")
@RequiredArgsConstructor
public class QrImageController {

    private final QrShareTokenStore tokenStore;
    private final QrCodeService qrCodeService;

    @GetMapping(value = "/{token}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getQr(@PathVariable String token) {
        String address = tokenStore.resolve(token);
        if (address == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] jpeg = qrCodeService.generateQrCodeJpeg(address);
        if (jpeg == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(MediaType.IMAGE_JPEG)
                .body(jpeg);
    }
}
