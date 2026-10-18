package com.example.walletbot.swap.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwapOrderStatusTest {

    @Test
    void terminalStatuses_areFinal() {
        assertThat(SwapOrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(SwapOrderStatus.REJECTED.isTerminal()).isTrue();
        assertThat(SwapOrderStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(SwapOrderStatus.FAILED_SOURCE.isTerminal()).isTrue();
        assertThat(SwapOrderStatus.FAILED_PROVIDER.isTerminal()).isTrue();
        assertThat(SwapOrderStatus.REFUNDED.isTerminal()).isTrue();
    }

    @Test
    void activeStatuses_areNotTerminal() {
        assertThat(SwapOrderStatus.QUOTED.isActive()).isTrue();
        assertThat(SwapOrderStatus.AUTHORIZED.isActive()).isTrue();
        assertThat(SwapOrderStatus.BROADCASTING.isActive()).isTrue();
        assertThat(SwapOrderStatus.SOURCE_CONFIRMED.isActive()).isTrue();
        assertThat(SwapOrderStatus.DEST_AWAITING.isActive()).isTrue();
        assertThat(SwapOrderStatus.REFUND_PENDING.isActive()).isTrue();
    }

    @Test
    void activeAndTerminal_arePartitions() {
        for (SwapOrderStatus status : SwapOrderStatus.values()) {
            assertThat(status.isActive()).isNotEqualTo(status.isTerminal());
        }
    }
}
