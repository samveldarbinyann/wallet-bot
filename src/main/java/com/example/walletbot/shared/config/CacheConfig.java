package com.example.walletbot.shared.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {
    private static CaffeineCache buildCache(String name, long maxSize, Duration ttl) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(ttl)
                        .recordStats()
                        .build()
        );
    }

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache("chart", 200, Duration.ofMinutes(15)),
                buildCache("users", 50_000, Duration.ofMinutes(5)),
                buildCache("swap-ranges", 200, Duration.ofMinutes(10)),
                buildCache("swap-estimate", 2000, Duration.ofSeconds(15)),
                buildCache("profile-photo", 1000, Duration.ofMinutes(5))
        ));
        return manager;
    }
}
