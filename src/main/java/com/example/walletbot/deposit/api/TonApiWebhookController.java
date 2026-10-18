package com.example.walletbot.deposit.api;

import com.example.walletbot.deposit.application.TonApiWebhookService;
import com.example.walletbot.deposit.application.TonApiWebhookSignatureVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/ton")
@RequiredArgsConstructor
public class TonApiWebhookController {

    private final TonApiWebhookSignatureVerifier verifier;
    private final TonApiWebhookService webhookService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] body,
            @RequestParam(value = "secret", required = false) String secret) {
        if (!verifier.isValid(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        webhookService.process(body);
        return ResponseEntity.ok().build();
    }
}
