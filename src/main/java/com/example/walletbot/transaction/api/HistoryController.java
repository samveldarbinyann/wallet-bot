package com.example.walletbot.transaction.api;

import com.example.walletbot.deposit.application.DepositScanService;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.transaction.api.dto.TransactionHistoryResponse;
import com.example.walletbot.transaction.application.HistoryService;
import com.example.walletbot.transaction.domain.TransactionDto;
import com.example.walletbot.user.domain.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class HistoryController {

    private final DepositScanService depositScanService;
    private final HistoryService historyService;

    @GetMapping("/history")
    public TransactionHistoryResponse getHistory(@CurrentUser UserEntity user,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(defaultValue = "true") boolean scan) {
        if (scan && page == 0) {
            depositScanService.scanUserWalletsAsync(user.getId());
        }
        var historyPage = historyService.findUnifiedHistory(user.getId(), page, size);
        return new TransactionHistoryResponse(
                historyPage.getContent(),
                historyPage.getTotalPages(),
                historyPage.getTotalElements(),
                historyPage.getNumber(),
                historyPage.getSize());
    }

    @GetMapping("/transactions/{id}")
    public TransactionDto getTransaction(@CurrentUser UserEntity user, @PathVariable Long id) {
        return historyService.getTransaction(user.getId(), id);
    }
}
