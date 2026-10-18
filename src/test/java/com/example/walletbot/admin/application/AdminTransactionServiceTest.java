package com.example.walletbot.admin.application;

import com.example.walletbot.transaction.domain.TransactionLogEntity;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTransactionServiceTest {

    @Mock
    TransactionLogRepository repo;

    @InjectMocks
    AdminTransactionService service;

    @Test
    void getByUser_delegatesWithRequestedPaging() {
        when(repo.findByUserIdOrderByDisplayTimeDesc(eq(1L), any())).thenReturn(Page.empty());

        service.getByUser(1L, 2, 25);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findByUserIdOrderByDisplayTimeDesc(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    void getByUser_capsPageSizeAt100() {
        when(repo.findByUserIdOrderByDisplayTimeDesc(eq(1L), any())).thenReturn(Page.empty());

        service.getByUser(1L, 0, 9999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findByUserIdOrderByDisplayTimeDesc(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void getAll_capsPageSizeAndSortsByCreatedAtDesc() {
        when(repo.findAll(any(PageRequest.class))).thenReturn(Page.empty());

        service.getAll(0, 500);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(repo).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }
}
