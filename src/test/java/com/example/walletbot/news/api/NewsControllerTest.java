package com.example.walletbot.news.api;

import com.example.walletbot.news.application.RssFeedService;
import com.example.walletbot.news.domain.NewsPageResponse;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NewsControllerTest {

    private RssFeedService rssFeedService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        rssFeedService = mock(RssFeedService.class);
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setLang("ru");
        mvc = MiniAppMvc.forController(new NewsController(rssFeedService), user);
    }

    @Test
    void getNews_defaultPage_usesUserLang() throws Exception {
        when(rssFeedService.getNews(0, "ru")).thenReturn(new NewsPageResponse(List.of(), 0, 1, "2026-05-30"));

        mvc.perform(get("/api/miniapp/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(rssFeedService).getNews(0, "ru");
    }

    @Test
    void getNews_explicitPage_passedThrough() throws Exception {
        when(rssFeedService.getNews(3, "ru")).thenReturn(new NewsPageResponse(List.of(), 3, 5, "2026-05-30"));

        mvc.perform(get("/api/miniapp/news").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(3));

        verify(rssFeedService).getNews(3, "ru");
    }
}
