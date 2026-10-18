package com.example.walletbot.user.application;

import com.example.walletbot.user.domain.EmailOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OtpAttemptService {

    private final EmailOtpRepository otpRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailure(Long otpId, int maxAttempts) {
        return otpRepo.incrementAttempts(otpId, maxAttempts);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUsed(Long otpId) {
        otpRepo.markUsed(otpId);
    }
}
