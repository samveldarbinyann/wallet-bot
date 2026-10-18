package com.example.walletbot.platform.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletLockServiceTest {

    @Mock
    EntityManager entityManager;
    @Mock
    Query query;

    private WalletLockService service;

    @BeforeEach
    void setUp() {
        service = new WalletLockService();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.createNativeQuery(any())).thenReturn(query);
        when(query.setParameter(eq("k"), any())).thenReturn(query);
    }

    @Test
    void withLock_lockAcquired_runsBodyAndReturnsValue() {
        when(query.getSingleResult()).thenReturn(Boolean.TRUE);

        WalletLockService.LockResult<String> result = service.withLock(5L, () -> "done");

        assertThat(result.acquired()).isTrue();
        assertThat(result.value()).isEqualTo("done");
    }

    @Test
    void withLock_lockNotAcquired_skipsBody() {
        when(query.getSingleResult()).thenReturn(Boolean.FALSE);
        @SuppressWarnings("unchecked")
        java.util.function.Supplier<String> body = mock(java.util.function.Supplier.class);

        WalletLockService.LockResult<String> result = service.withLock(5L, body);

        assertThat(result.acquired()).isFalse();
        assertThat(result.value()).isNull();
        org.mockito.Mockito.verify(body, org.mockito.Mockito.never()).get();
    }
}
