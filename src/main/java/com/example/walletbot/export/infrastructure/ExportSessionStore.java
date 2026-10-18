package com.example.walletbot.export.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ExportSessionStore {

    public enum RevealResult {OK, EXPIRED, WRONG_USER}

    public void open(String sid, long userId, int tokenVersion) {
        cache.put(sid, new SessionState(userId, tokenVersion, false));
    }

    public RevealOutcome tryReveal(String sid, long userId, int tokenVersion) {
        RevealOutcome[] result = {null};
        cache.asMap().compute(sid, (k, v) -> {
            if (v == null || v.burned()) {
                result[0] = new RevealOutcome(RevealResult.EXPIRED);
                return v;
            }
            if (v.userId() != userId) {
                result[0] = new RevealOutcome(RevealResult.WRONG_USER);
                return burnSession(v);
            }
            if (v.tokenVersion() != tokenVersion) {
                result[0] = new RevealOutcome(RevealResult.EXPIRED);
                return burnSession(v);
            }
            result[0] = new RevealOutcome(RevealResult.OK);
            return v;
        });
        return result[0];
    }

    private final Cache<String, SessionState> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(3))
            .maximumSize(50_000)
            .build();

    private SessionState burnSession(SessionState v) {
        return new SessionState(v.userId(), v.tokenVersion(), true);
    }

    public record RevealOutcome(RevealResult result) {
    }

    public void burn(String sid) {
        cache.asMap().computeIfPresent(sid, (k, v) -> burnSession(v));
    }

    public record SessionState(long userId, int tokenVersion, boolean burned) {
    }
}