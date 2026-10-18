package com.example.walletbot.asset.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.event.WalletDepositEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DepositBalanceInvalidationListenerTest {

    private final AssetService assetService = mock(AssetService.class);
    private final UserEventStream userEventStream = mock(UserEventStream.class);
    private final DepositBalanceInvalidationListener listener =
            new DepositBalanceInvalidationListener(assetService, userEventStream);

    @Test
    void onDeposit_withCreditDelta_creditsBalanceWithoutReReading() {
        listener.onDeposit(new WalletDepositEvent(42L, 7L, BlockchainNetwork.USDT_TRC20,
                new BigDecimal("50"), "0xpay"));

        verify(assetService).creditBalance(42L, 7L, BlockchainNetwork.USDT_TRC20, new BigDecimal("50"));
        verify(assetService, never()).invalidateUserBalances(any());
        verify(assetService, never()).warmUserBalances(any());
        verify(userEventStream).pushBalanceChanged(42L);
    }

    @Test
    void onDeposit_withoutDelta_invalidatesAndWarms() {
        listener.onDeposit(new WalletDepositEvent(42L));

        verify(assetService).invalidateUserBalances(42L);
        verify(assetService).warmUserBalances(42L);
        verify(assetService, never()).creditBalance(any(), any(), any(), any());
        verify(userEventStream).pushBalanceChanged(42L);
    }
}
