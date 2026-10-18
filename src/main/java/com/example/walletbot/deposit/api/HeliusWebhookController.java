package com.example.walletbot.deposit.api;

import com.example.walletbot.deposit.application.HeliusWebhookService;
import com.example.walletbot.deposit.application.HeliusWebhookSignatureVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/helius")
@RequiredArgsConstructor
public class HeliusWebhookController {

    private final HeliusWebhookSignatureVerifier verifier;
    private final HeliusWebhookService webhookService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (!verifier.isValid(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        webhookService.process(body);
        return ResponseEntity.ok().build();
    }
}
