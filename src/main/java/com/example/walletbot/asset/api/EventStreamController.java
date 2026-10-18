package com.example.walletbot.asset.api;

import com.example.walletbot.asset.application.UserEventStream;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.user.domain.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class EventStreamController {

    private final UserEventStream userEventStream;

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@CurrentUser UserEntity user) {
        return userEventStream.register(user.getId());
    }
}