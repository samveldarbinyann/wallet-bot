package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.swap.domain.SwapRanges;
import com.example.walletbot.swap.provider.simpleswap.dto.SimpleSwapRangesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SimpleSwapCache {

    private final SimpleSwapClient client;


    private static SwapRanges toDomain(SimpleSwapRangesDto dto) {
        return new SwapRanges(
                dto.min() != null ? new BigDecimal(dto.min()) : null,
                dto.max() != null ? new BigDecimal(dto.max()) : null);
    }

    @Cacheable(value = "swap-ranges",
            key = "#from.ticker() + ':' + #from.network() + '|' + #to.ticker() + ':' + #to.network()",
            sync = true)
    public Optional<SwapRanges> ranges(SimpleSwapCurrency from, SimpleSwapCurrency to) {
        return client.getRanges(from, to).map(SimpleSwapCache::toDomain);
    }

    @Cacheable(value = "swap-estimate",
            key = "#from.ticker() + ':' + #from.network() + '|' + #to.ticker() + ':' + #to.network() + '|' + #amount.toPlainString()",
            sync = true)
    public BigDecimal estimate(SimpleSwapCurrency from, SimpleSwapCurrency to, BigDecimal amount) {
        return client.getEstimated(from, to, amount).orElse(null);
    }
}
