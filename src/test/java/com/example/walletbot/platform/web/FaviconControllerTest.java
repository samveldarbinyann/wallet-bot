package com.example.walletbot.platform.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FaviconControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FaviconController()).build();
    }

    @Test
    void faviconGet_returnsSvgWithCacheHeader() throws Exception {
        mvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")));
    }

    @Test
    void faviconHead_returnsOkSvg() throws Exception {
        mvc.perform(head("/favicon.ico"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"));
    }
}
