package com.example.walletbot.news.application;

import com.example.walletbot.news.domain.NewsItemDto;
import com.example.walletbot.news.domain.NewsPageResponse;
import com.example.walletbot.news.infrastructure.NewsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RssFeedServiceTest {

    private static final String EN_URL = "https://en.example/rss";
    private static final String RU_URL = "https://ru.example/rss";

    @Mock
    RestTemplate restTemplate;

    private NewsProperties props;
    private RssFeedService service;

    @BeforeEach
    void setUp() {
        props = new NewsProperties();
        props.setPageSize(2);
        props.setCacheTtlMinutes(10);
        props.setFeeds(List.of(feed(EN_URL, "CryptoEN", "en")));
        service = new RssFeedService(restTemplate, props, Runnable::run);
    }

    @Test
    void getNews_parsesAllFields() {
        stub(EN_URL, rss(item(
                "<title>Bitcoin News</title>"
                        + "<link>https://example.com/a</link>"
                        + "<description>&lt;b&gt;Big &amp;amp; bold&lt;/b&gt;   news</description>"
                        + "<pubDate>Tue, 10 Jun 2025 09:00:00 GMT</pubDate>"
                        + "<enclosure url=\"https://img.example/a.jpg\" type=\"image/jpeg\" length=\"1\"/>")));

        NewsItemDto dto = service.getNews(0, "en").news().getFirst();

        assertThat(dto.id()).isEqualTo("2dce0a4c50441bfc");
        assertThat(dto.title()).isEqualTo("Bitcoin News");
        assertThat(dto.url()).isEqualTo("https://example.com/a");
        assertThat(dto.source()).isEqualTo("CryptoEN");
        assertThat(dto.description()).isEqualTo("Big & bold news");
        assertThat(dto.publishedAt()).isEqualTo("2025-06-10T09:00:00Z");
        assertThat(dto.image()).isEqualTo("https://img.example/a.jpg");
    }

    @Test
    void getNews_sortsByPublishedAtDescending() {
        stub(EN_URL, rss(
                item("<title>Older</title><link>https://example.com/b</link>"
                        + "<pubDate>Tue, 10 Jun 2025 09:00:00 GMT</pubDate>"),
                item("<title>Newer</title><link>https://example.com/a</link>"
                        + "<pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        List<NewsItemDto> news = service.getNews(0, "en").news();

        assertThat(news).extracting(NewsItemDto::title).containsExactly("Newer", "Older");
    }

    @Test
    void getNews_paginatesByPageSize() {
        stub(EN_URL, rss(
                item("<title>1</title><link>https://example.com/a</link><pubDate>Wed, 11 Jun 2025 12:00:00 GMT</pubDate>"),
                item("<title>2</title><link>https://example.com/b</link><pubDate>Wed, 11 Jun 2025 11:00:00 GMT</pubDate>"),
                item("<title>3</title><link>https://example.com/c</link><pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        NewsPageResponse first = service.getNews(0, "en");
        assertThat(first.news()).extracting(NewsItemDto::title).containsExactly("1", "2");
        assertThat(first.currentPage()).isZero();
        assertThat(first.totalPages()).isEqualTo(2);

        NewsPageResponse second = service.getNews(1, "en");
        assertThat(second.news()).extracting(NewsItemDto::title).containsExactly("3");
        assertThat(second.currentPage()).isEqualTo(1);
    }

    @Test
    void getNews_clampsPageBeyondRangeToLastPage() {
        stub(EN_URL, rss(
                item("<title>1</title><link>https://example.com/a</link><pubDate>Wed, 11 Jun 2025 12:00:00 GMT</pubDate>"),
                item("<title>2</title><link>https://example.com/b</link><pubDate>Wed, 11 Jun 2025 11:00:00 GMT</pubDate>"),
                item("<title>3</title><link>https://example.com/c</link><pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        NewsPageResponse page = service.getNews(99, "en");

        assertThat(page.currentPage()).isEqualTo(1);
        assertThat(page.news()).extracting(NewsItemDto::title).containsExactly("3");
    }

    @Test
    void getNews_emptyFeed_returnsSinglePageNoItems() {
        stub(EN_URL, rss());

        NewsPageResponse page = service.getNews(0, "en");

        assertThat(page.news()).isEmpty();
        assertThat(page.currentPage()).isZero();
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void getNews_truncatesLongDescription() {
        String longText = "x".repeat(250);
        stub(EN_URL, rss(item("<title>T</title><link>https://example.com/a</link>"
                + "<description>" + longText + "</description>"
                + "<pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        String description = service.getNews(0, "en").news().getFirst().description();

        assertThat(description).hasSize(201).endsWith("…");
    }

    @Test
    void getNews_fallsBackToMediaContentWhenNoEnclosure() {
        stub(EN_URL, rss(item("<title>T</title><link>https://example.com/a</link>"
                + "<media:content url=\"https://img.example/media.jpg\" medium=\"image\"/>"
                + "<pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        assertThat(service.getNews(0, "en").news().getFirst().image())
                .isEqualTo("https://img.example/media.jpg");
    }

    @Test
    void getNews_imageNullWhenNoEnclosureOrMedia() {
        stub(EN_URL, rss(item("<title>T</title><link>https://example.com/a</link>"
                + "<pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        assertThat(service.getNews(0, "en").news().getFirst().image()).isNull();
    }

    @Test
    void getNews_missingTitle_usesPlaceholder() {
        stub(EN_URL, rss(item("<link>https://example.com/a</link>"
                + "<pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        assertThat(service.getNews(0, "en").news().getFirst().title()).isEqualTo("No title");
    }

    @Test
    void getNews_skipsEntryWithoutUrl() {
        stub(EN_URL, rss(
                item("<title>No URL</title><pubDate>Wed, 11 Jun 2025 12:00:00 GMT</pubDate>"),
                item("<title>Has URL</title><link>https://example.com/a</link>"
                        + "<pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        assertThat(service.getNews(0, "en").news())
                .extracting(NewsItemDto::title)
                .containsExactly("Has URL");
    }

    @Test
    void getNews_cachesWithinTtl_fetchesFeedOnce() {
        stub(EN_URL, rss(item("<title>T</title><link>https://example.com/a</link>"
                + "<pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        service.getNews(0, "en");
        service.getNews(0, "en");

        verify(restTemplate, times(1)).getForObject(URI.create(EN_URL), byte[].class);
    }

    @Test
    void getNews_selectsFeedsMatchingLanguage() {
        props.setFeeds(List.of(feed(EN_URL, "CryptoEN", "en"), feed(RU_URL, "CryptoRU", "ru")));
        stub(RU_URL, rss(item("<title>Новости</title><link>https://example.com/a</link>"
                + "<pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));

        NewsPageResponse page = service.getNews(0, "ru");

        assertThat(page.news()).extracting(NewsItemDto::source).containsOnly("CryptoRU");
        verify(restTemplate, times(1)).getForObject(URI.create(RU_URL), byte[].class);
    }

    @Test
    void getNews_oneFeedFailing_doesNotBreakOthers() {
        props.setFeeds(List.of(feed(EN_URL, "Good", "en"), feed(RU_URL, "Bad", "en")));
        stub(EN_URL, rss(item("<title>Survivor</title><link>https://example.com/a</link>"
                + "<pubDate>Wed, 11 Jun 2025 10:00:00 GMT</pubDate>")));
        when(restTemplate.getForObject(URI.create(RU_URL), byte[].class))
                .thenThrow(new RuntimeException("feed down"));

        assertThat(service.getNews(0, "en").news())
                .extracting(NewsItemDto::title)
                .containsExactly("Survivor");
    }

    private static NewsProperties.FeedSource feed(String url, String name, String lang) {
        NewsProperties.FeedSource f = new NewsProperties.FeedSource();
        f.setUrl(url);
        f.setName(name);
        f.setLang(lang);
        return f;
    }

    private static String item(String inner) {
        return "<item>" + inner + "</item>";
    }

    private static byte[] rss(String... items) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\">"
                + "<channel><title>Feed</title>"
                + String.join("", items)
                + "</channel></rss>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private void stub(String url, byte[] body) {
        when(restTemplate.getForObject(URI.create(url), byte[].class)).thenReturn(body);
    }
}
