package com.example.walletbot.news.infrastructure;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "news")
public class NewsProperties {

    @Min(1)
    private long cacheTtlMinutes = 10;
    @Min(1)
    private int pageSize = 20;
    private List<FeedSource> feeds = List.of();

    @Data
    public static class FeedSource {
        private String url;
        private String name;
        private String lang = "en";
    }
}
