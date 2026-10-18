package com.example.walletbot.swap.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.domain.SwapPair;
import com.example.walletbot.swap.domain.SwapRanges;
import com.example.walletbot.swap.routing.SwapRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwapRangeService {

    private static final SwapRanges EMPTY = new SwapRanges(null, null);

    private final SwapRouter router;

    @Value("${swap.providers.simpleswap.prewarm-on-start:true}")
    private boolean prewarmOnStart;

    public SwapRanges range(BlockchainNetwork from, BlockchainNetwork to) {
        if (from == to) {
            return EMPTY;
        }
        SwapPair pair = SwapPair.of(from, to);
        if (!router.supports(pair)) {
            return EMPTY;
        }
        try {
            return router.rangesFor(pair).orElse(EMPTY);
        } catch (RuntimeException e) {
            log.warn("Ranges lookup failed for {}: {}", pair, e.getMessage());
            return EMPTY;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void prewarm() {
        if (!prewarmOnStart) return;
        CompletableFuture.runAsync(() -> {
            int warmed = 0;
            BlockchainNetwork[] networks = BlockchainNetwork.values();
            for (BlockchainNetwork from : networks) {
                for (BlockchainNetwork to : networks) {
                    if (from == to) continue;
                    SwapPair pair = SwapPair.of(from, to);
                    if (!router.supports(pair)) continue;
                    try {
                        router.rangesFor(pair);
                        warmed++;
                    } catch (RuntimeException e) {
                        log.debug("Prewarm ranges failed for {}: {}", pair, e.getMessage());
                    }
                }
            }
            log.info("Swap ranges prewarmed: {} pairs", warmed);
        });
    }
}
