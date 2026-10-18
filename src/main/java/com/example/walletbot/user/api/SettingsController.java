package com.example.walletbot.user.api;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.shared.constant.SupportedLanguage;
import com.example.walletbot.user.api.dto.FiatRatesResponse;
import com.example.walletbot.user.api.dto.I18nResponse;
import com.example.walletbot.user.api.dto.SettingsUpdateRequest;
import com.example.walletbot.user.api.dto.UserResponse;
import com.example.walletbot.user.application.UserMapper;
import com.example.walletbot.user.application.UserService;
import com.example.walletbot.user.domain.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class SettingsController {

    private final UserService userService;
    private final MessageService messageService;
    private final PriceService priceService;
    private final UserMapper userMapper;
    private final UserRestMapper userRestMapper;

    @GetMapping("/settings/user")
    public UserResponse getUser(@CurrentUser UserEntity user) {
        return userRestMapper.toRestDto(userMapper.toDto(user));
    }

    @PutMapping("/settings")
    public UserResponse updateSettings(@CurrentUser UserEntity user,
                                      @RequestBody SettingsUpdateRequest request) {
        userService.updateSettings(user, request.lang(), request.currency(), request.timezone(),
                request.theme());
        return userRestMapper.toRestDto(userMapper.toDto(user));
    }

    @GetMapping("/fiat-rates")
    public FiatRatesResponse getFiatRates() {
        PriceService.FiatRatesView view = priceService.getSupportedFiatRates();
        return new FiatRatesResponse(view.rates(), view.symbols());
    }

    @GetMapping("/i18n")
    public I18nResponse getTranslations(@RequestParam(name = "lang", required = false) String lang) {
        SupportedLanguage resolved = SupportedLanguage.fromTag(lang);
        String code = (resolved != null ? resolved : SupportedLanguage.DEFAULT).name();
        return new I18nResponse(code, messageService.getAllUiMessages(code));
    }
}
