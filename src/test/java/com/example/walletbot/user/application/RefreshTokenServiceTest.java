package com.example.walletbot.user.application;

import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.security.HashingService;
import com.example.walletbot.user.domain.RefreshTokenEntity;
import com.example.walletbot.user.domain.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository repo;
    @Mock
    HashingService hashingService;

    @InjectMocks
    RefreshTokenService service;

    private static RefreshTokenEntity entity(Long id, Long userId) {
        RefreshTokenEntity e = new RefreshTokenEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setIssuedAt(Instant.now());
        e.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        return e;
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshTtlDays", 30L);
        when(hashingService.sha256Hex(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
    }

    @Test
    void issue_persistsHashedTokenWithFutureExpiry() {
        RefreshTokenService.Issued issued = service.issue(5L);

        assertThat(issued.rawToken()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(Instant.now());

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repo).saveAndFlush(captor.capture());
        RefreshTokenEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getTokenHash()).isEqualTo("hash:" + issued.rawToken());
        assertThat(saved.getExpiresAt()).isAfter(saved.getIssuedAt());
    }

    @Test
    void validate_unknownToken_throwsBadRequest() {
        when(repo.findByTokenHash("hash:nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate("nope"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.invalid_refresh_token");
    }

    @Test
    void validate_revokedToken_throwsRevoked() {
        RefreshTokenEntity stored = entity(1L, 9L);
        stored.setRevokedAt(Instant.now());
        when(repo.findByTokenHash("hash:old")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.validate("old"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.refresh_token_revoked");
    }

    @Test
    void validate_expiredToken_throwsExpired() {
        RefreshTokenEntity stored = entity(1L, 9L);
        stored.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(repo.findByTokenHash("hash:old")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.validate("old"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.refresh_token_expired");
    }

    @Test
    void validate_activeToken_returnsUserId_andDoesNotMutate() {
        RefreshTokenEntity stored = entity(1L, 9L);
        stored.setExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        when(repo.findByTokenHash("hash:old")).thenReturn(Optional.of(stored));

        assertThat(service.validate("old")).isEqualTo(9L);

        verify(repo, never()).saveAndFlush(any());
        verify(repo, never()).revokeAllActiveForUser(anyLong(), any(Instant.class));
    }

    @Test
    void revokeAll_delegatesToRepo() {
        service.revokeAll(3L);
        verify(repo).revokeAllActiveForUser(eq(3L), any(Instant.class));
    }
}
