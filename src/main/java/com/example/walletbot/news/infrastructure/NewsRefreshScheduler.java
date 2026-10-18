package com.example.walletbot.news.infrastructure;

import com.example.walletbot.news.application.RssFeedService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "news.refresh.enabled", havingValue = "true", matchIfMissing = true)
public class NewsRefreshScheduler {

    private static final long REFRESH_DELAY_MS = 8 * 60 * 1000;

    private final RssFeedService rssFeedService;
    private final NewsProperties props;

    @PostConstruct
    void validateCadence() {
        long ttlMs = props.getCacheTtlMinutes() * 60_000L;
        if (REFRESH_DELAY_MS >= ttlMs) {
            throw new IllegalStateException(
                    "news.cache-ttl-minutes (%d min) must exceed the scheduler's %d min refresh cadence, or the cache expires between refreshes"
                            .formatted(props.getCacheTtlMinutes(), REFRESH_DELAY_MS / 60_000));
        }
    }

    @Scheduled(fixedDelay = REFRESH_DELAY_MS, initialDelay = 0)
    public void refresh() {
        rssFeedService.refreshAll();
    }
}