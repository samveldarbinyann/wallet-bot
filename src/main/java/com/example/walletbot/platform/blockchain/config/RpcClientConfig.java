package com.example.walletbot.platform.blockchain.config;

import com.example.walletbot.platform.blockchain.client.SolRpcClient;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.platform.blockchain.client.TronGridClient;
import com.example.walletbot.platform.blockchain.ratelimit.TonCenterRateLimiter;
import com.example.walletbot.platform.blockchain.ratelimit.TronGridRateLimiter;
import com.example.walletbot.platform.blockchain.util.RpcEndpointResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RpcClientConfig {

    @Bean
    public TonCenterRateLimiter tonCenterRateLimiter(BlockchainProperties blockchain) {
        return new TonCenterRateLimiter(blockchain.ton().rateLimitPerSecond());
    }

    @Bean
    public TonCenterClient tonCenterClient(RestTemplate restTemplate, RpcProperties rpc,
                                           TonCenterRateLimiter tonCenterRateLimiter) {
        return new TonCenterClient(restTemplate, rpc.ton().url(), rpc.ton().key(), tonCenterRateLimiter);
    }

    @Bean
    public TronGridClient tronGridClient(RestTemplate restTemplate, RpcProperties rpc,
                                         TronGridRateLimiter tronGridRateLimiter) {
        return new TronGridClient(restTemplate, rpc.tron().url(), rpc.tron().key(), tronGridRateLimiter);
    }

    @Bean
    public SolRpcClient solRpcClient(RestTemplate restTemplate, RpcProperties rpc) {
        return new SolRpcClient(restTemplate, RpcEndpointResolver.buildUrl(rpc.sol().url(), rpc.sol().key()));
    }
}