package com.example.walletbot.swap.provider;

import com.example.walletbot.swap.domain.*;

import java.math.BigDecimal;
import java.util.Optional;

public interface SwapProvider {

    String providerId();

    boolean supports(SwapPair pair);

    default int priority() {
        return 100;
    }

    default Optional<SwapRanges> ranges(SwapPair pair) {
        return Optional.empty();
    }

    SwapQuote quote(SwapPair pair, BigDecimal amountIn);

    PreparedSwap prepare(SwapQuote quote, String fromAddress, String toAddress);

    SubmittedSwap submit(PreparedSwap prepared, WalletKeySource keys);

    SwapStatusUpdate track(SubmittedSwap submitted);
}
