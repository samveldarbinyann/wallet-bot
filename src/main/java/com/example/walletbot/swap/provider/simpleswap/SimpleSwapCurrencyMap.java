package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class SimpleSwapCurrencyMap {

    private final Map<BlockchainNetwork, SimpleSwapCurrency> currencies =
            new EnumMap<>(BlockchainNetwork.class);

    public SimpleSwapCurrencyMap() {
        currencies.put(BlockchainNetwork.ETH, new SimpleSwapCurrency("eth", "eth"));
        currencies.put(BlockchainNetwork.BTC, new SimpleSwapCurrency("btc", "btc"));
        currencies.put(BlockchainNetwork.SOL, new SimpleSwapCurrency("sol", "sol"));
        currencies.put(BlockchainNetwork.TON, new SimpleSwapCurrency("ton", "ton"));
        currencies.put(BlockchainNetwork.TRX, new SimpleSwapCurrency("trx", "trx"));
        currencies.put(BlockchainNetwork.USDT_ERC20, new SimpleSwapCurrency("usdt", "eth"));
        currencies.put(BlockchainNetwork.USDT_TRC20, new SimpleSwapCurrency("usdt", "trx"));
        currencies.put(BlockchainNetwork.USDT_TON, new SimpleSwapCurrency("usdt", "ton"));
        currencies.put(BlockchainNetwork.USDT_SOL, new SimpleSwapCurrency("usdt", "sol"));
        currencies.put(BlockchainNetwork.USDC_ERC20, new SimpleSwapCurrency("usdc", "eth"));
        currencies.put(BlockchainNetwork.USDC_SOL, new SimpleSwapCurrency("usdc", "sol"));
    }

    public SimpleSwapCurrency currency(BlockchainNetwork network) {
        SimpleSwapCurrency c = currencies.get(network);
        if (c == null) {
            throw new IllegalArgumentException("SimpleSwap currency not configured for: " + network);
        }
        return c;
    }

    public boolean supports(BlockchainNetwork network) {
        return currencies.containsKey(network);
    }
}
