package com.example.walletbot.notification.infrastructure;

import com.example.walletbot.notification.domain.BroadcastRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastImageCleanupScheduler {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastImageStorage imageStorage;

    @Scheduled(cron = "0 0 4 * * *")
    public void removeOrphanImages() {
        Set<String> referenced = Set.copyOf(broadcastRepository.findAllImageUrls());
        int removed = imageStorage.deleteUnreferenced(referenced, Duration.ofHours(1));
        if (removed > 0) {
            log.info("Removed {} orphan broadcast image(s)", removed);
        }
    }
}
