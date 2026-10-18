package com.example.walletbot.user.application;

import com.example.walletbot.shared.constant.SupportedCurrency;
import com.example.walletbot.shared.constant.SupportedLanguage;
import com.example.walletbot.shared.constant.UiTheme;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.user.domain.NotificationPreferencesUpdate;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import com.example.walletbot.user.infrastructure.UserCacheService;
import com.example.walletbot.wallet.application.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public List<Long> findActiveUserIds(Instant since) {
        return userRepository.findUserIdsActiveSince(since);
    }

    @Transactional(readOnly = true)
    public List<Long> findInactiveUserIds(Instant before) {
        return userRepository.findUserIdsInactiveBefore(before);
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(username.trim());
    }

    public UserEntity getOrCreateUser(Long telegramId, String username, String firstName, String lastName,
                                      String languageCode) {
        return userRepository.findByTelegramId(telegramId)
                .map(existing -> {
                    boolean changed = false;
                    if (!Objects.equals(username, existing.getUsername())) {
                        reclaimUsername(username, existing.getId());
                        existing.setUsername(username);
                        changed = true;
                    }
                    if (firstName != null && !firstName.equals(existing.getFirstName())) {
                        existing.setFirstName(firstName);
                        changed = true;
                    }
                    if (lastName != null && !lastName.equals(existing.getLastName())) {
                        existing.setLastName(lastName);
                        changed = true;
                    }
                    return changed ? userRepository.save(existing) : existing;
                })
                .orElseGet(() -> {
                    reclaimUsername(username, null);
                    UserEntity newUser = new UserEntity();
                    newUser.setTelegramId(telegramId);
                    newUser.setUsername(username);
                    newUser.setFirstName(firstName);
                    newUser.setLastName(lastName);
                    SupportedLanguage detected = SupportedLanguage.fromTag(languageCode);
                    if (detected != null) {
                        newUser.setLang(detected.name());
                    }
                    UserEntity saved = userRepository.save(newUser);
                    walletService.createAllWallets(saved);
                    return saved;
                });
    }

    private void reclaimUsername(String username, Long exceptUserId) {
        if (username == null) return;
        userRepository.clearUsername(username, exceptUserId);
    }

    public void updateSettings(UserEntity user, String lang, String currency, String timezone, String theme) {
        boolean changed = false;
        changed |= applyEnum(lang, SupportedLanguage::fromTag, user::setLang);
        changed |= applyEnum(currency, SupportedCurrency::from, user::setCurrency);
        changed |= applyEnum(theme, UiTheme::from, user::setTheme);
        if (timezone != null && !timezone.isBlank()) {
            if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
                throw new BadRequestException("error.bad_request");
            }
            user.setTimezone(timezone);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
            userCacheService.evict(user.getId());
        }
    }

    private boolean applyEnum(String raw, Function<String, ? extends Enum<?>> parser, Consumer<String> setter) {
        if (raw == null || raw.isBlank()) return false;
        Enum<?> parsed = parser.apply(raw);
        if (parsed == null) throw new BadRequestException("error.bad_request");
        setter.accept(parsed.name());
        return true;
    }

    public void updateLastLoginAt(UserEntity user) {
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        userCacheService.evict(user.getId());
    }

    public void updateNotificationPreferences(UserEntity user, NotificationPreferencesUpdate req) {
        if (req.pushEnabled() != null) user.setNotifyPushEnabled(req.pushEnabled());
        if (req.emailEnabled() != null) user.setNotifyEmailEnabled(req.emailEnabled());
        userRepository.save(user);
        userCacheService.evict(user.getId());
    }
}
