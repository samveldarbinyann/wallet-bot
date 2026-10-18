package com.example.walletbot.admin.application;

import com.example.walletbot.transaction.domain.TransactionLogEntity;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminTransactionService {

    private final TransactionLogRepository transactionLogRepository;

    public Page<TransactionLogEntity> getByUser(Long userId, int page, int size) {
        return transactionLogRepository.findByUserIdOrderByDisplayTimeDesc(
                userId, PageRequest.of(page, Math.min(size, 100)));
    }

    public Page<TransactionLogEntity> getAll(int page, int size) {
        return transactionLogRepository.findAll(
                PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()));
    }
}
