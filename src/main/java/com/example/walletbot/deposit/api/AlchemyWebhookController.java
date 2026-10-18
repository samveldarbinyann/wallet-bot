package com.example.walletbot.deposit.api;

import com.example.walletbot.deposit.application.AlchemyWebhookService;
import com.example.walletbot.deposit.application.AlchemyWebhookSignatureVerifier;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/webhooks/alchemy")
@RequiredArgsConstructor
public class AlchemyWebhookController {

    private final AlchemyWebhookSignatureVerifier verifier;
    private final AlchemyWebhookService webhookService;

    @PostMapping("/{network}")
    public ResponseEntity<Void> receive(
            @PathVariable String network,
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Alchemy-Signature", required = false) String signature) {
        BlockchainNetwork chain = parseChain(network);
        if (chain == null) {
            return ResponseEntity.notFound().build();
        }
        if (!verifier.isValid(chain, body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        webhookService.process(chain, body);
        return ResponseEntity.ok().build();
    }

    private BlockchainNetwork parseChain(String network) {
        return switch (network.toLowerCase(Locale.ROOT)) {
            case "eth" -> BlockchainNetwork.ETH;
            case "bnb" -> BlockchainNetwork.BNB;
            default -> null;
        };
    }
}