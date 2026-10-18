package com.example.walletbot.swap.application.intent;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.security.IntentTokenCodec;
import com.example.walletbot.swap.domain.SwapQuote;
import com.example.walletbot.swap.domain.exception.SwapIntentInvalidException;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class SwapIntentService {

    private static final String TYPE = "swap_intent";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_FROM = "fn";
    private static final String CLAIM_TO = "tn";
    private static final String CLAIM_AMT_IN = "ain";
    private static final String CLAIM_AMT_OUT = "aout";
    private static final String CLAIM_AMT_OUT_MIN = "aoutmin";
    private static final String CLAIM_PROVIDER = "p";
    private static final String CLAIM_PROVIDER_QID = "pqid";
    private static final String CLAIM_FROM_ADDR = "fadr";
    private static final String CLAIM_TO_ADDR = "tadr";

    private final IntentTokenCodec intentCodec;
    private final long intentTtlSeconds;

    public SwapIntentService(IntentTokenCodec intentCodec,
                             @Value("${swap.intent.ttl-seconds:600}") long intentTtlSeconds) {
        this.intentCodec = intentCodec;
        this.intentTtlSeconds = intentTtlSeconds;
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    public IntentToken issue(long userId, SwapQuote quote,
                             String fromAddress, String toAddress) {
        String jti = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(intentTtlSeconds);
        String token = intentCodec.builder()
                .id(jti)
                .claim(CLAIM_TYPE, TYPE)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_FROM, quote.pair().from().name())
                .claim(CLAIM_TO, quote.pair().to().name())
                .claim(CLAIM_AMT_IN, quote.amountIn().toPlainString())
                .claim(CLAIM_AMT_OUT, quote.amountOut().toPlainString())
                .claim(CLAIM_AMT_OUT_MIN, quote.amountOutMin().toPlainString())
                .claim(CLAIM_PROVIDER, quote.providerId())
                .claim(CLAIM_PROVIDER_QID, quote.providerQuoteId() == null ? "" : quote.providerQuoteId())
                .claim(CLAIM_FROM_ADDR, fromAddress)
                .claim(CLAIM_TO_ADDR, toAddress)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .compact();
        return new IntentToken(jti, token, expiry);
    }

    public SwapIntent verify(String token, long callerUserId) {
        try {
            Claims claims = intentCodec.parse(token);
            if (!TYPE.equals(claims.get(CLAIM_TYPE))) {
                throw new SwapIntentInvalidException("Wrong intent type");
            }
            long uid = ((Number) claims.get(CLAIM_UID)).longValue();
            if (uid != callerUserId) {
                throw new SwapIntentInvalidException("Intent token does not belong to caller");
            }
            return new SwapIntent(
                    claims.getId(),
                    uid,
                    BlockchainNetwork.valueOf((String) claims.get(CLAIM_FROM)),
                    BlockchainNetwork.valueOf((String) claims.get(CLAIM_TO)),
                    new BigDecimal((String) claims.get(CLAIM_AMT_IN)),
                    new BigDecimal((String) claims.get(CLAIM_AMT_OUT)),
                    new BigDecimal((String) claims.get(CLAIM_AMT_OUT_MIN)),
                    (String) claims.get(CLAIM_PROVIDER),
                    nullIfBlank((String) claims.get(CLAIM_PROVIDER_QID)),
                    (String) claims.get(CLAIM_FROM_ADDR),
                    (String) claims.get(CLAIM_TO_ADDR),
                    claims.getExpiration().toInstant()
            );
        } catch (SwapIntentInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new SwapIntentInvalidException("Invalid or expired swap intent token");
        }
    }

    public record IntentToken(String jti, String token, Instant validUntil) {
    }
}
