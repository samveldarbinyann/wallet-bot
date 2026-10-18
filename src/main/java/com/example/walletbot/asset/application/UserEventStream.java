package com.example.walletbot.asset.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class UserEventStream {

    private static final long TIMEOUT_MS = Duration.ofMinutes(15).toMillis();

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> set = emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        set.add(emitter);
        emitter.onCompletion(() -> set.remove(emitter));
        emitter.onTimeout(() -> {
            set.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> set.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("{}"));
        } catch (IOException e) {
            set.remove(emitter);
        }
        return emitter;
    }

    public void pushBalanceChanged(Long userId) {
        if (userId == null) return;
        Set<SseEmitter> set = emitters.get(userId);
        if (set == null || set.isEmpty()) return;
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name("balance").data("{}"));
            } catch (Exception e) {
                set.remove(emitter);
            }
        }
    }
}
