package com.example.walletbot.deposit.infrastructure;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.event.WalletCreatedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.Executor;

@Component
public class TonApiAddressRegistrationListener {

    private final TonApiWebhookRegistrar registrar;
    private final Executor ioExecutor;

    public TonApiAddressRegistrationListener(TonApiWebhookRegistrar registrar,
                                             @Qualifier("ioExecutor") Executor ioExecutor) {
        this.registrar = registrar;
        this.ioExecutor = ioExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletCreated(WalletCreatedEvent event) {
        if (event.network() != BlockchainNetwork.TON || !registrar.isConfigured()) {
            return;
        }
        ioExecutor.execute(() -> registrar.subscribe(List.of(event.address())));
    }
}
