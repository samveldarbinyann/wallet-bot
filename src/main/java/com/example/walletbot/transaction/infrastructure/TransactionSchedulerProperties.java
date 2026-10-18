package com.example.walletbot.transaction.infrastructure;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@ConfigurationProperties(prefix = "transaction.scheduler.status-poller")
public class TransactionSchedulerProperties {

    private static final Map<BlockchainNetwork, Duration> DEFAULT_TIMEOUTS = defaultTimeouts();
    private static final Duration DEFAULT_FALLBACK_TIMEOUT = Duration.ofHours(2);

    private boolean enabled = true;
    private Duration delay = Duration.ofSeconds(15);
    private Duration initialDelay = Duration.ofSeconds(10);
    private int batchSize = 100;
    private Map<BlockchainNetwork, Duration> timeouts = new EnumMap<>(DEFAULT_TIMEOUTS);
    private Duration fallbackTimeout = DEFAULT_FALLBACK_TIMEOUT;

    private static Map<BlockchainNetwork, Duration> defaultTimeouts() {
        Map<BlockchainNetwork, Duration> map = new EnumMap<>(BlockchainNetwork.class);
        map.put(BlockchainNetwork.ETH, Duration.ofHours(2));
        map.put(BlockchainNetwork.BNB, Duration.ofHours(2));
        map.put(BlockchainNetwork.BTC, Duration.ofHours(24));
        map.put(BlockchainNetwork.SOL, Duration.ofMinutes(15));
        map.put(BlockchainNetwork.TON, Duration.ofMinutes(30));
        map.put(BlockchainNetwork.TRX, Duration.ofMinutes(20));
        return map;
    }

    public Duration timeoutFor(BlockchainNetwork network) {
        BlockchainNetwork resolved = network.getParentNetwork() != null
                ? network.getParentNetwork() : network;
        Duration v = timeouts.get(resolved);
        return v != null ? v : fallbackTimeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDelay() {
        return delay;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Map<BlockchainNetwork, Duration> getTimeouts() {
        return timeouts;
    }

    public void setTimeouts(Map<BlockchainNetwork, Duration> timeouts) {
        this.timeouts = new EnumMap<>(DEFAULT_TIMEOUTS);
        if (timeouts != null) this.timeouts.putAll(timeouts);
    }

    public Duration getFallbackTimeout() {
        return fallbackTimeout;
    }

    public void setFallbackTimeout(Duration fallbackTimeout) {
        this.fallbackTimeout = fallbackTimeout;
    }
}
