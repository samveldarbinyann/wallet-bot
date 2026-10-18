package com.example.walletbot.swap.infrastructure;

import com.example.walletbot.swap.provider.simpleswap.SimpleSwapClient;
import com.example.walletbot.swap.provider.simpleswap.SimpleSwapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(SimpleSwapProperties.class)
public class SwapHttpClientConfig {

    @Bean
    public SimpleSwapClient simpleSwapClient(SimpleSwapProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        return new SimpleSwapClient(builder, properties);
    }
}
