package com.example.walletbot.transaction.application;

import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.user.application.UserService;
import com.example.walletbot.user.domain.ProfilePhotoPort;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RecipientResolverTest {

    private UserService userService;
    private ProfilePhotoPort profilePhotoPort;
    private RecipientResolver resolver;
    private UserEntity caller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        profilePhotoPort = mock(ProfilePhotoPort.class);
        resolver = new RecipientResolver(userService, profilePhotoPort);
        caller = user(1L, "sender", "Alice", null);
    }

    private static UserEntity user(long id, String username, String firstName, String lastName) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        return u;
    }

    @Test
    void lookup_found_notSelf_buildsDisplayName() {
        when(userService.findByUsername("bobby")).thenReturn(Optional.of(user(2L, "bobby", "Bobby", "Jones")));

        RecipientResolver.RecipientLookup result = resolver.lookup("bobby", caller);

        assertThat(result.found()).isTrue();
        assertThat(result.self()).isFalse();
        assertThat(result.username()).isEqualTo("bobby");
        assertThat(result.displayName()).isEqualTo("Bobby Jones");
    }

    @Test
    void lookup_stripsLeadingAt_andIsCaseInsensitivePassthrough() {
        when(userService.findByUsername("Bobby")).thenReturn(Optional.of(user(2L, "Bobby", null, null)));

        RecipientResolver.RecipientLookup result = resolver.lookup("  @Bobby ", caller);

        assertThat(result.found()).isTrue();
        assertThat(result.displayName()).isNull();
        verify(userService).findByUsername("Bobby");
    }

    @Test
    void lookup_self_flagsSelf() {
        when(userService.findByUsername("sender")).thenReturn(Optional.of(user(1L, "sender", "Alice", null)));

        RecipientResolver.RecipientLookup result = resolver.lookup("sender", caller);

        assertThat(result.found()).isTrue();
        assertThat(result.self()).isTrue();
    }

    @Test
    void lookup_unknownUser_notFound() {
        when(userService.findByUsername("ghost")).thenReturn(Optional.empty());

        RecipientResolver.RecipientLookup result = resolver.lookup("ghost", caller);

        assertThat(result.found()).isFalse();
        assertThat(result.username()).isEqualTo("ghost");
    }

    @Test
    void lookup_invalidHandle_shortCircuitsWithoutQuery() {
        RecipientResolver.RecipientLookup result = resolver.lookup("no", caller);

        assertThat(result.found()).isFalse();
        assertThat(result.username()).isNull();
        verifyNoInteractions(userService);
    }

    @Test
    void resolve_found_returnsUser() {
        UserEntity recipient = user(2L, "bobby", "Bobby", null);
        when(userService.findByUsername("bobby")).thenReturn(Optional.of(recipient));

        assertThat(resolver.resolve("@bobby")).isSameAs(recipient);
    }

    @Test
    void resolve_unknown_throws() {
        when(userService.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("ghost"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getMessageKey())
                        .isEqualTo("error.recipient_not_found"));
    }

    @Test
    void resolve_invalidHandle_throws() {
        assertThatThrownBy(() -> resolver.resolve("bad handle!"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(userService);
    }

    @Test
    void fetchPhoto_found_delegatesToProfilePhotoPort() {
        UserEntity recipient = user(2L, "bobby", "Bobby", null);
        recipient.setTelegramId(555L);
        byte[] photo = {1, 2, 3};
        when(userService.findByUsername("bobby")).thenReturn(Optional.of(recipient));
        when(profilePhotoPort.fetchPhoto(555L)).thenReturn(Optional.of(photo));

        assertThat(resolver.fetchPhoto("@bobby")).containsSame(photo);
    }

    @Test
    void fetchPhoto_unknownUser_empty() {
        when(userService.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(resolver.fetchPhoto("ghost")).isEmpty();
        verifyNoInteractions(profilePhotoPort);
    }

    @Test
    void fetchPhoto_invalidHandle_shortCircuitsWithoutQuery() {
        assertThat(resolver.fetchPhoto("no")).isEmpty();
        verifyNoInteractions(userService, profilePhotoPort);
    }
}