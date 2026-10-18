package com.example.walletbot.news.application;

import com.example.walletbot.news.domain.NewsItemDto;
import com.example.walletbot.news.domain.NewsPageResponse;
import com.example.walletbot.news.infrastructure.NewsProperties;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class RssFeedService {

    private static final List<String> SUPPORTED_LANGS = List.of("en", "ru");

    private final RestTemplate restTemplate;
    private final NewsProperties props;
    private final Executor ioExecutor;

    private final Map<String, CacheEntry> cacheByLang = new ConcurrentHashMap<>();
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

    public RssFeedService(@Qualifier("newsRestTemplate") RestTemplate restTemplate,
                          NewsProperties props,
                          @Qualifier("ioExecutor") Executor ioExecutor) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.ioExecutor = ioExecutor;
    }

    private static String effectiveLang(String lang) {
        return (lang != null && lang.startsWith("ru")) ? "ru" : "en";
    }

    public NewsPageResponse getNews(int page, String lang) {
        String effectiveLang = effectiveLang(lang);
        CacheEntry entry = cacheByLang.get(effectiveLang);

        if (entry == null) {
            entry = refreshOnAbsent(effectiveLang);
        } else if (System.currentTimeMillis() >= entry.expiresAt()) {
            triggerAsyncRefresh(effectiveLang);
        }

        List<NewsItemDto> items = entry.items();
        int pageSize = props.getPageSize();
        int totalPages = (int) Math.ceil((double) items.size() / pageSize);
        int safePage = Math.clamp(page, 0, Math.max(0, totalPages - 1));

        List<NewsItemDto> pageItems = items.stream()
                .skip((long) safePage * pageSize)
                .limit(pageSize)
                .toList();

        return new NewsPageResponse(pageItems, safePage, Math.max(totalPages, 1), entry.fetchedAt());
    }

    public void refreshAll() {
        SUPPORTED_LANGS.forEach(this::triggerAsyncRefresh);
    }

    private synchronized CacheEntry refreshOnAbsent(String lang) {
        CacheEntry current = cacheByLang.get(lang);
        return current != null ? current : refresh(lang);
    }

    private void triggerAsyncRefresh(String lang) {
        if (!refreshing.add(lang)) return;
        try {
            CompletableFuture.runAsync(() -> refresh(lang), ioExecutor)
                    .whenComplete((v, ex) -> {
                        refreshing.remove(lang);
                        if (ex != null) {
                            log.warn("Async news refresh failed for lang={}: {}", lang, ex.getMessage(), ex);
                        }
                    });
        } catch (RejectedExecutionException ex) {
            refreshing.remove(lang);
            log.warn("News refresh executor rejected task for lang={}: {}", lang, ex.getMessage());
        }
    }

    private CacheEntry refresh(String lang) {
        List<CompletableFuture<List<NewsItemDto>>> futures = props.getFeeds().stream()
                .filter(feed -> lang.equals(feed.getLang()))
                .map(feed -> CompletableFuture.supplyAsync(() -> fetchFeedSafe(feed), ioExecutor))
                .toList();

        List<NewsItemDto> fetched = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        Map<String, NewsItemDto> deduped = new LinkedHashMap<>();
        for (NewsItemDto item : fetched) {
            deduped.putIfAbsent(item.id(), item);
        }

        List<NewsItemDto> all = deduped.values().stream()
                .sorted(Comparator.comparing(NewsItemDto::publishedAt).reversed())
                .toList();

        CacheEntry previous = cacheByLang.get(lang);
        if (all.isEmpty() && previous != null && !previous.items().isEmpty()) {
            log.warn("News refresh for lang={} returned 0 articles; keeping {} previously cached", lang, previous.items().size());
            CacheEntry kept = new CacheEntry(
                    previous.items(),
                    System.currentTimeMillis() + props.getCacheTtlMinutes() * 60_000L,
                    previous.fetchedAt());
            cacheByLang.put(lang, kept);
            return kept;
        }

        CacheEntry fresh = new CacheEntry(
                all,
                System.currentTimeMillis() + props.getCacheTtlMinutes() * 60_000L,
                Instant.now().toString());
        cacheByLang.put(lang, fresh);
        log.info("News cache refreshed for lang={}: {} articles", lang, all.size());
        return fresh;
    }

    private static String sha256Prefix(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private List<NewsItemDto> fetchFeedSafe(NewsProperties.FeedSource source) {
        try {
            return fetchFeed(source);
        } catch (Exception ex) {
            log.warn("Failed to fetch RSS feed [{}]: {}", source.getName(), ex.getMessage(), ex);
            return List.of();
        }
    }

    private record CacheEntry(List<NewsItemDto> items, long expiresAt, String fetchedAt) {
    }

    private List<NewsItemDto> fetchFeed(NewsProperties.FeedSource source) throws Exception {
        byte[] bytes = restTemplate.getForObject(URI.create(source.getUrl()), byte[].class);
        if (bytes == null) return Collections.emptyList();

        SyndFeedInput input = new SyndFeedInput();
        input.setXmlHealerOn(true);
        SyndFeed feed = input.build(new XmlReader(new ByteArrayInputStream(bytes), "UTF-8"));

        List<NewsItemDto> result = new ArrayList<>();
        for (SyndEntry entry : feed.getEntries()) {
            String url = entry.getLink();
            if (url == null || url.isBlank()) url = entry.getUri();
            if (url == null || url.isBlank()) continue;

            String publishedAt = entry.getPublishedDate() != null
                    ? entry.getPublishedDate().toInstant()
                      .atOffset(ZoneOffset.UTC)
                      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    : Instant.now().toString();

            String description = "";
            if (entry.getDescription() != null) {
                String raw = entry.getDescription().getValue();
                if (raw != null) {
                    description = HtmlUtils.htmlUnescape(
                            raw.replaceAll("<[^>]+>", "")
                                    .replaceAll("\\s+", " ")
                                    .trim()
                    );
                    if (description.length() > 200) description = description.substring(0, 200) + "…";
                }
            }

            String imageUrl = null;
            if (!entry.getEnclosures().isEmpty()) {
                imageUrl = entry.getEnclosures().getFirst().getUrl();
            }
            if (imageUrl == null) {
                MediaEntryModule media = (MediaEntryModule) entry.getModule(MediaEntryModule.URI);
                if (media != null && media.getMediaContents().length > 0) {
                    MediaContent mc = media.getMediaContents()[0];
                    if (mc.getReference() != null) imageUrl = mc.getReference().toString();
                }
            }

            result.add(new NewsItemDto(
                    sha256Prefix(url),
                    entry.getTitle() != null ? entry.getTitle().trim() : "No title",
                    url,
                    source.getName(),
                    description,
                    publishedAt,
                    imageUrl
            ));
        }
        return result;
    }
}
