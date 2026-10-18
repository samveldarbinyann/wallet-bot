package com.example.walletbot.transaction.api;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.platform.web.validation.NetworkParser;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.NotFoundException;
import com.example.walletbot.shared.exception.RateLimitException;
import com.example.walletbot.transaction.api.dto.*;
import com.example.walletbot.transaction.application.RecipientLookupRateLimiter;
import com.example.walletbot.transaction.application.RecipientResolver;
import com.example.walletbot.transaction.application.SendIntentService;
import com.example.walletbot.transaction.application.SendTransactionService;
import com.example.walletbot.transaction.domain.TransferValidator;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class SendController {

    private final SendTransactionService sendTransactionService;
    private final NetworkParser networkParser;
    private final TransferValidator transferValidator;
    private final SendIntentService sendIntentService;
    private final WalletService walletService;
    private final PriceService priceService;
    private final RecipientResolver recipientResolver;
    private final RecipientLookupRateLimiter recipientLookupRateLimiter;
    @Qualifier("ioExecutor")
    private final Executor ioExecutor;

    @PostMapping("/send/estimate")
    public EstimateResponse estimate(@CurrentUser UserEntity user,
                                     @Valid @RequestBody EstimateRequest request) {
        BlockchainNetwork net = networkParser.parseStrict(request.network());
        TransferValidator.Target target = transferValidator.validate(user, net, request.address(), request.amount());
        boolean max = Boolean.TRUE.equals(request.max());
        return buildEstimateResponse(user, net, target, request.amount(), max);
    }

    @GetMapping("/send/recipient")
    public RecipientLookupResponse lookupRecipient(@CurrentUser UserEntity user,
                                                   @RequestParam String username) {
        if (!recipientLookupRateLimiter.isAllowed(user.getId())) {
            throw new RateLimitException("Too many lookups");
        }
        RecipientResolver.RecipientLookup lookup = recipientResolver.lookup(username, user);
        if (lookup.found()) {
            recipientLookupRateLimiter.markResolved(user.getId(), username);
        }
        return new RecipientLookupResponse(
                lookup.found(), lookup.username(), lookup.displayName(), lookup.self());
    }

    @GetMapping("/send/recipient/photo")
    public CompletableFuture<ResponseEntity<byte[]>> recipientPhoto(@CurrentUser UserEntity user,
                                                                     @RequestParam String username) {
        if (!recipientLookupRateLimiter.isAllowedForEstimate(user.getId(), username)) {
            throw new RateLimitException("Too many lookups");
        }
        return CompletableFuture.supplyAsync(() -> recipientResolver.fetchPhoto(username)
                .map(photo -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(photo))
                .orElseGet(() -> ResponseEntity.notFound().build()), ioExecutor);
    }

    @PostMapping("/send/estimate/username")
    public EstimateResponse estimateByUsername(@CurrentUser UserEntity user,
                                               @Valid @RequestBody UsernameEstimateRequest request) {
        if (!recipientLookupRateLimiter.isAllowedForEstimate(user.getId(), request.username())) {
            throw new RateLimitException("Too many lookups");
        }
        BlockchainNetwork net = networkParser.parseStrict(request.network());
        UserEntity recipient = recipientResolver.resolve(request.username());
        recipientLookupRateLimiter.markResolved(user.getId(), request.username());
        WalletEntity recipientWallet = walletService.getWalletForNetworkOrThrow(recipient, net);
        TransferValidator.Target target =
                transferValidator.validate(user, net, recipientWallet.getAddress(), request.amount());
        boolean max = Boolean.TRUE.equals(request.max());
        return buildEstimateResponse(user, net, target, request.amount(), max);
    }

    private EstimateResponse buildEstimateResponse(UserEntity user, BlockchainNetwork net,
                                                   TransferValidator.Target target, BigDecimal amount, boolean max) {
        SendTransactionService.EstimateResult est =
                sendTransactionService.buildEstimate(user, target.wallet(), net, amount, max);
        String intentToken = sendIntentService.create(
                user.getId(), target.wallet().getId(), net, target.canonicalTo(), amount, max);
        return new EstimateResponse(
                est.maxSendable(),
                net.getSymbol(),
                est.hasEnoughGas(),
                est.gasShortfall(),
                intentToken,
                est.recipientAmount(),
                est.networkFee()
        );
    }

    @PostMapping("/send/fee-preview")
    public FeePreviewResponse feePreview(@CurrentUser UserEntity user,
                                         @Valid @RequestBody FeePreviewRequest request) {
        BlockchainNetwork net = networkParser.parseStrict(request.network());
        WalletEntity wallet = walletService.getWalletForNetwork(user, net);
        if (wallet == null) {
            throw new NotFoundException("Wallet not found");
        }
        SendTransactionService.EstimateResult est =
                sendTransactionService.buildEstimate(user, wallet, net, null, false);
        return new FeePreviewResponse(
                est.networkFee(),
                est.hasEnoughGas(),
                net.getGasNetwork().getSymbol(),
                net.getMinSend(),
                priceService.usdValue(net.getAsset().getSymbol(), net.getMinSend())
        );
    }

    @PostMapping("/send")
    public SendResponse send(@CurrentUser UserEntity user,
                             @Valid @RequestBody SendRequest request,
                             @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        SendIntentService.ResolvedTransfer transfer = sendIntentService.resolve(user, request.intentToken());
        SendTransactionService.SendResult result =
                sendTransactionService.executeWithIdempotency(user, transfer, idemKey);
        return new SendResponse(true, result.txHash(), result.txId());
    }

}
