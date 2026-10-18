package com.example.walletbot.swap.routing;

import com.example.walletbot.swap.domain.SwapPair;
import com.example.walletbot.swap.provider.SwapProvider;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class ProviderRegistry {

    private final List<SwapProvider> providers;

    public ProviderRegistry(List<SwapProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public Optional<SwapProvider> byId(String providerId) {
        return providers.stream()
                .filter(p -> p.providerId().equals(providerId))
                .findFirst();
    }

    public List<SwapProvider> supporting(SwapPair pair) {
        return providers.stream()
                .filter(p -> p.supports(pair))
                .sorted(Comparator.comparingInt(SwapProvider::priority))
                .toList();
    }
}
