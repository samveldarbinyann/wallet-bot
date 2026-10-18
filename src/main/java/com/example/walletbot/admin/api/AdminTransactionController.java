package com.example.walletbot.admin.api;

import com.example.walletbot.admin.api.dto.AdminTransactionDto;
import com.example.walletbot.admin.application.AdminTransactionMapper;
import com.example.walletbot.admin.application.AdminTransactionService;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/transactions")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final AdminTransactionService adminTransactionService;
    private final AdminTransactionMapper adminTransactionMapper;
    private final AdminTransactionRestMapper adminTransactionRestMapper;

    @GetMapping("/user/{userId}")
    public Page<AdminTransactionDto> getUserTransactions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminTransactionService.getByUser(userId, page, size).map(this::toRestDto);
    }

    @GetMapping
    public Page<AdminTransactionDto> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminTransactionService.getAll(page, size).map(this::toRestDto);
    }

    private AdminTransactionDto toRestDto(TransactionLogEntity entity) {
        return adminTransactionRestMapper.toRestDto(adminTransactionMapper.toDto(entity));
    }
}
