package com.example.walletbot.user.application;

import com.example.walletbot.shared.constant.SupportedLanguage;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.user.domain.NotificationPreferencesUpdate;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.domain.UserRepository;
import com.example.walletbot.user.infrastructure.UserCacheService;
import com.example.walletbot.wallet.application.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    UserCacheService userCacheService;
    @Mock
    WalletService walletService;

    @InjectMocks
    UserService userService;

    private UserEntity existingUser;

    @BeforeEach
    void setUp() {
        existingUser = new UserEntity();
        existingUser.setId(1L);
        existingUser.setTelegramId(100L);
        existingUser.setUsername("sam");
        existingUser.setFirstName("Sam");
        existingUser.setLastName("D");

        lenient().when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getOrCreateUser_existingUser_noChange_noSave() {
        when(userRepository.findByTelegramId(100L)).thenReturn(Optional.of(existingUser));

        UserEntity result = userService.getOrCreateUser(100L, "sam", "Sam", "D", null);

        assertThat(result).isSameAs(existingUser);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(walletService);
    }

    @Test
    void getOrCreateUser_existingUser_usernameChanged_saves() {
        when(userRepository.findByTelegramId(100L)).thenReturn(Optional.of(existingUser));

        UserEntity result = userService.getOrCreateUser(100L, "new_sam", "Sam", "D", null);

        assertThat(result.getUsername()).isEqualTo("new_sam");
        verify(userRepository).save(existingUser);
    }

    @Test
    void getOrCreateUser_existingUser_firstNameChanged_saves() {
        when(userRepository.findByTelegramId(100L)).thenReturn(Optional.of(existingUser));

        userService.getOrCreateUser(100L, "sam", "NewName", "D", null);

        verify(userRepository).save(existingUser);
    }

    @Test
    void getOrCreateUser_newUser_savesAndCreatesWallets() {
        when(userRepository.findByTelegramId(200L)).thenReturn(Optional.empty());

        UserEntity result = userService.getOrCreateUser(200L, "new_user", "New", "User", "ru");

        verify(userRepository).save(any(UserEntity.class));
        verify(walletService).createAllWallets(any(UserEntity.class));
        assertThat(result.getTelegramId()).isEqualTo(200L);
        assertThat(result.getUsername()).isEqualTo("new_user");
        assertThat(result.getLang()).isEqualTo("ru");
    }

    @Test
    void getOrCreateUser_newUser_localeTagNormalisedToSupported() {
        when(userRepository.findByTelegramId(200L)).thenReturn(Optional.empty());

        UserEntity result = userService.getOrCreateUser(200L, "u", null, null, "en-US");

        assertThat(result.getLang()).isEqualTo("en");
    }

    @Test
    void getOrCreateUser_newUser_unsupportedLocale_keepsDefault() {
        when(userRepository.findByTelegramId(200L)).thenReturn(Optional.empty());

        UserEntity result = userService.getOrCreateUser(200L, "u", null, null, "fr");

        assertThat(result.getLang()).isEqualTo(SupportedLanguage.DEFAULT.name());
    }

    @Test
    void getOrCreateUser_existingUser_telegramLocaleDoesNotOverwriteStoredLang() {
        existingUser.setLang("ru");
        when(userRepository.findByTelegramId(100L)).thenReturn(Optional.of(existingUser));

        UserEntity result = userService.getOrCreateUser(100L, "sam", "Sam", "D", "en");

        assertThat(result).isSameAs(existingUser);
        assertThat(result.getLang()).isEqualTo("ru");
    }

    @Test
    void getOrCreateUser_existingUser_handleRemoved_clearsUsernameAndSaves() {
        when(userRepository.findByTelegramId(100L)).thenReturn(Optional.of(existingUser));

        UserEntity result = userService.getOrCreateUser(100L, null, "Sam", "D", null);

        assertThat(result.getUsername()).isNull();
        verify(userRepository).save(existingUser);
    }

    @Test
    void getOrCreateUser_existingUser_usernameChanged_reclaimsOldHolderAtomically() {
        when(userRepository.findByTelegramId(100L)).thenReturn(Optional.of(existingUser));
        when(userRepository.clearUsername("new_sam", 1L)).thenReturn(0);

        userService.getOrCreateUser(100L, "new_sam", "Sam", "D", null);

        verify(userRepository).clearUsername("new_sam", 1L);
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    void getOrCreateUser_existingUser_handleReclaimed_excludesOwnRowFromEviction() {
        when(userRepository.findByTelegramId(100L)).thenReturn(Optional.of(existingUser));
        when(userRepository.clearUsername("new_sam", 1L)).thenReturn(1);

        UserEntity result = userService.getOrCreateUser(100L, "new_sam", "Sam", "D", null);

        assertThat(result.getUsername()).isEqualTo("new_sam");
        verify(userRepository).clearUsername("new_sam", 1L);
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    void getOrCreateUser_newUser_handleReclaimed_clearsOtherRowAtomically() {
        when(userRepository.findByTelegramId(200L)).thenReturn(Optional.empty());
        when(userRepository.clearUsername("new_user", null)).thenReturn(1);

        UserEntity result = userService.getOrCreateUser(200L, "new_user", "New", "User", "ru");

        assertThat(result.getUsername()).isEqualTo("new_user");
        verify(userRepository).clearUsername("new_user", null);
        verify(walletService).createAllWallets(any(UserEntity.class));
    }

    @Test
    void getOrCreateUser_newUser_noUsername_skipsReclaim() {
        when(userRepository.findByTelegramId(200L)).thenReturn(Optional.empty());

        userService.getOrCreateUser(200L, null, "New", "User", null);

        verify(userRepository, never()).clearUsername(any(), any());
    }

    @Test
    void findByUsername_returnsMatch() {
        UserEntity match = new UserEntity();
        match.setId(2L);
        match.setUsername("bobby");
        when(userRepository.findByUsernameIgnoreCase("bobby")).thenReturn(Optional.of(match));

        Optional<UserEntity> result = userService.findByUsername(" bobby ");

        assertThat(result).containsSame(match);
        verify(userRepository).findByUsernameIgnoreCase("bobby");
    }

    @Test
    void findByUsername_noMatch_empty() {
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThat(userService.findByUsername("ghost")).isEmpty();
    }

    @Test
    void findByUsername_blank_shortCircuitsWithoutQuery() {
        assertThat(userService.findByUsername("  ")).isEmpty();
        assertThat(userService.findByUsername(null)).isEmpty();
        verify(userRepository, never()).findByUsernameIgnoreCase(any());
    }

    @Test
    void updateSettings_nonNullLang_savesAndEvicts() {
        userService.updateSettings(existingUser, "en", null, null, null);

        assertThat(existingUser.getLang()).isEqualTo("en");
        verify(userRepository).save(existingUser);
        verify(userCacheService).evict(1L);
    }

    @Test
    void updateSettings_langTagNormalised() {
        userService.updateSettings(existingUser, "RU", null, null, null);

        assertThat(existingUser.getLang()).isEqualTo("ru");
    }

    @Test
    void updateSettings_unsupportedLang_throwsAndDoesNotSave() {
        assertThatThrownBy(() -> userService.updateSettings(existingUser, "fr", null, null, null))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userCacheService);
    }

    @Test
    void updateSettings_currencyNormalised() {
        userService.updateSettings(existingUser, null, "EUR", null, null);

        assertThat(existingUser.getCurrency()).isEqualTo("eur");
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateSettings_unsupportedCurrency_throwsAndDoesNotSave() {
        assertThatThrownBy(() -> userService.updateSettings(existingUser, null, "btc", null, null))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userCacheService);
    }

    @Test
    void updateSettings_validTimezone_saves() {
        userService.updateSettings(existingUser, null, null, "Europe/Moscow", null);

        assertThat(existingUser.getTimezone()).isEqualTo("Europe/Moscow");
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateSettings_invalidTimezone_throwsAndDoesNotSave() {
        assertThatThrownBy(() -> userService.updateSettings(existingUser, null, null, "Mars/Olympus", null))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userCacheService);
    }

    @Test
    void updateSettings_allNull_noSave() {
        userService.updateSettings(existingUser, null, null, null, null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userCacheService);
    }

    @Test
    void updateSettings_allBlank_noSave() {
        userService.updateSettings(existingUser, "", " ", "", "");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateSettings_currency_savesAndEvicts() {
        userService.updateSettings(existingUser, null, "eur", null, null);

        assertThat(existingUser.getCurrency()).isEqualTo("eur");
        verify(userRepository).save(existingUser);
        verify(userCacheService).evict(1L);
    }

    @Test
    void updateSettings_themeNormalised_saves() {
        userService.updateSettings(existingUser, null, null, null, "DARK");

        assertThat(existingUser.getTheme()).isEqualTo("dark");
        verify(userRepository).save(existingUser);
        verify(userCacheService).evict(1L);
    }

    @Test
    void updateSettings_unsupportedTheme_throwsAndDoesNotSave() {
        assertThatThrownBy(() -> userService.updateSettings(existingUser, null, null, null, "neon"))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userCacheService);
    }

    @Test
    void updateNotificationPreferences_nonNullFields_savesAndEvicts() {
        var req = new NotificationPreferencesUpdate(false, true);

        userService.updateNotificationPreferences(existingUser, req);

        assertThat(existingUser.isNotifyPushEnabled()).isFalse();
        assertThat(existingUser.isNotifyEmailEnabled()).isTrue();
        verify(userRepository).save(existingUser);
        verify(userCacheService).evict(1L);
    }

    @Test
    void updateNotificationPreferences_allNull_stillSavesAndEvicts() {
        var req = new NotificationPreferencesUpdate(null, null);

        userService.updateNotificationPreferences(existingUser, req);

        verify(userRepository).save(existingUser);
        verify(userCacheService).evict(1L);
    }
}
