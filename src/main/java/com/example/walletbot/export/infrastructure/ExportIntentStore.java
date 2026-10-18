package com.example.walletbot.export.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ExportIntentStore {

    public enum State {ISSUED, CONSUMED}

    private final Cache<String, State> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(6))
            .maximumSize(50_000)
            .build();

    public void issue(String jti) {
        cache.put(jti, State.ISSUED);
    }

    public State peek(String jti) {
        return cache.getIfPresent(jti);
    }

    public State consume(String jti) {
        State[] result = {null};
        cache.asMap().compute(jti, (k, v) -> {
            if (v == State.ISSUED) {
                result[0] = State.ISSUED;
                return State.CONSUMED;
            }
            result[0] = v;
            return v;
        });
        return result[0];
    }
}