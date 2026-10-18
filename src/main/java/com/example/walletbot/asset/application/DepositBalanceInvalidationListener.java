package com.example.walletbot.asset.application;

import com.example.walletbot.shared.event.WalletDepositEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DepositBalanceInvalidationListener {

    private final AssetService assetService;
    private final UserEventStream userEventStream;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeposit(WalletDepositEvent event) {
        if (event.hasCredit()) {
            assetService.creditBalance(event.userId(), event.walletId(), event.network(), event.amount());
        } else {
            assetService.invalidateUserBalances(event.userId());
            assetService.warmUserBalances(event.userId());
        }
        userEventStream.pushBalanceChanged(event.userId());
    }
}
