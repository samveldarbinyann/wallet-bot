package com.example.walletbot.deposit.infrastructure;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.event.WalletCreatedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;

@Component
public class HeliusAddressRegistrationListener {

    private final HeliusWebhookRegistrar registrar;
    private final Executor ioExecutor;

    public HeliusAddressRegistrationListener(HeliusWebhookRegistrar registrar,
                                             @Qualifier("ioExecutor") Executor ioExecutor) {
        this.registrar = registrar;
        this.ioExecutor = ioExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletCreated(WalletCreatedEvent event) {
        if (event.network() != BlockchainNetwork.SOL || !registrar.isConfigured()) {
            return;
        }
        ioExecutor.execute(registrar::syncAddresses);
    }
}
