package com.example.walletbot.admin.api;

import com.example.walletbot.admin.application.AdminTransactionMapperImpl;
import com.example.walletbot.admin.application.AdminTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminTransactionControllerTest {

    private AdminTransactionService service;
    private AdminTransactionController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminTransactionService.class);
        controller = new AdminTransactionController(
                service, new AdminTransactionMapperImpl(), new AdminTransactionRestMapperImpl());
    }

    @Test
    void getUserTransactions_delegatesWithPaging() {
        when(service.getByUser(eq(5L), anyInt(), anyInt())).thenReturn(Page.empty());

        var result = controller.getUserTransactions(5L, 1, 10);

        assertThat(result).isEmpty();
        verify(service).getByUser(5L, 1, 10);
    }

    @Test
    void getAllTransactions_defaultPaging() {
        when(service.getAll(anyInt(), anyInt())).thenReturn(Page.empty());

        var result = controller.getAllTransactions(0, 20);

        assertThat(result).isEmpty();
        verify(service).getAll(0, 20);
    }
}
