package com.example.walletbot.user.api;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.application.UserMapperImpl;
import com.example.walletbot.user.application.UserService;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsControllerTest {

    private final UserEntity user = new UserEntity();
    private UserService userService;
    private MessageService messageService;
    private PriceService priceService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        user.setTelegramId(100L);
        user.setLang("en");
        userService = mock(UserService.class);
        messageService = mock(MessageService.class);
        priceService = mock(PriceService.class);
        SettingsController controller = new SettingsController(
                userService, messageService, priceService, new UserMapperImpl(), new UserRestMapperImpl());
        mvc = MiniAppMvc.forController(controller, user);
    }

    @Test
    void getUser_returnsMappedUser() throws Exception {
        mvc.perform(get("/api/miniapp/settings/user"))
                .andExpect(status().isOk());
    }

    @Test
    void updateSettings_delegatesToUserService() throws Exception {
        mvc.perform(put("/api/miniapp/settings")
                        .contentType(APPLICATION_JSON)
                        .content("{\"lang\":\"ru\",\"currency\":\"EUR\",\"timezone\":\"UTC\",\"theme\":\"dark\"}"))
                .andExpect(status().isOk());

        verify(userService).updateSettings(eq(user), eq("ru"), eq("EUR"), eq("UTC"), eq("dark"));
    }

    @Test
    void getFiatRates_returnsRatesAndSymbolsFromService() throws Exception {
        when(priceService.getSupportedFiatRates()).thenReturn(new PriceService.FiatRatesView(
                Map.of("usd", new BigDecimal("1.0"), "eur", new BigDecimal("0.9")),
                Map.of("usd", "$", "eur", "€")));

        mvc.perform(get("/api/miniapp/fiat-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rates.eur").value(0.9))
                .andExpect(jsonPath("$.symbols.eur").value("€"));
    }

    @Test
    void getTranslations_returnsLangAndMessages() throws Exception {
        when(messageService.getAllUiMessages("en")).thenReturn(Map.of("ui.x", "X"));

        mvc.perform(get("/api/miniapp/i18n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("en"))
                .andExpect(jsonPath("$.messages['ui.x']").value("X"));
    }
}
