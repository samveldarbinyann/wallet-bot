package com.example.walletbot.transaction.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.security.IntentTokenCodec;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class SendIntentService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_WALLET = "wid";
    private static final String CLAIM_TO = "to";
    private static final String CLAIM_AMOUNT = "amt";
    private static final String CLAIM_NETWORK = "net";
    private static final String CLAIM_MAX = "max";
    private static final String INTENT_TYPE = "send_intent";

    private final IntentTokenCodec intentCodec;
    private WalletService walletService;

    public SendIntentService(IntentTokenCodec intentCodec) {
        this.intentCodec = intentCodec;
    }

    @Autowired
    public void setWalletService(WalletService walletService) {
        this.walletService = walletService;
    }

    public String create(long userId, long walletId, BlockchainNetwork network,
                         String canonicalTo, BigDecimal amount, boolean max) {
        Instant now = Instant.now();
        return intentCodec.builder()
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, INTENT_TYPE)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_WALLET, walletId)
                .claim(CLAIM_NETWORK, network.name())
                .claim(CLAIM_TO, canonicalTo)
                .claim(CLAIM_AMOUNT, amount.toPlainString())
                .claim(CLAIM_MAX, max)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                .compact();
    }

    public SendIntent verify(String token, long callerUserId) {
        try {
            Claims claims = intentCodec.parse(token);
            if (!INTENT_TYPE.equals(claims.get(CLAIM_TYPE))) {
                throw new BadRequestException("error.intent_token_invalid_type");
            }
            long uid = ((Number) claims.get(CLAIM_UID)).longValue();
            if (uid != callerUserId) {
                throw new BadRequestException("error.intent_token_user_mismatch");
            }
            long walletId = ((Number) claims.get(CLAIM_WALLET)).longValue();
            String networkName = (String) claims.get(CLAIM_NETWORK);
            String canonicalTo = (String) claims.get(CLAIM_TO);
            String amountStr = (String) claims.get(CLAIM_AMOUNT);
            boolean max = Boolean.TRUE.equals(claims.get(CLAIM_MAX));
            String jti = claims.getId();
            BlockchainNetwork network;
            try {
                network = BlockchainNetwork.valueOf(networkName);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("error.intent_token_invalid_network");
            }
            return new SendIntent(jti, walletId, network, canonicalTo, new BigDecimal(amountStr), max);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("error.intent_token_invalid_or_expired");
        }
    }

    public ResolvedTransfer resolve(UserEntity user, String intentToken) {
        SendIntent intent = verify(intentToken, user.getId());
        WalletEntity wallet = walletService.getWalletForNetwork(user, intent.network());
        if (wallet == null || wallet.getId() != intent.walletId()) {
            throw new BadRequestException("error.intent_wallet_mismatch");
        }
        return new ResolvedTransfer(intent.jti(), intent.network(), wallet,
                intent.canonicalTo(), intent.amount(), intent.max());
    }

    public record SendIntent(String jti, long walletId, BlockchainNetwork network,
                             String canonicalTo, BigDecimal amount, boolean max) {
    }

    public record ResolvedTransfer(String jti, BlockchainNetwork net, WalletEntity wallet,
                                   String canonicalTo, BigDecimal amount, boolean max) {
    }
}