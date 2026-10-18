package com.example.walletbot.platform.blockchain.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RpcEndpointResolverTest {

    @Test
    void buildUrl_blankBase_returnsEmpty() {
        assertThat(RpcEndpointResolver.buildUrl(null, "key")).isEmpty();
        assertThat(RpcEndpointResolver.buildUrl("  ", "key")).isEmpty();
    }

    @Test
    void buildUrl_noApiKey_returnsTrimmedBase() {
        assertThat(RpcEndpointResolver.buildUrl("  https://rpc.example/  ", null)).isEqualTo("https://rpc.example/");
        assertThat(RpcEndpointResolver.buildUrl("https://rpc.example/", "  ")).isEqualTo("https://rpc.example/");
    }

    @Test
    void buildUrl_withApiKey_appendsTrimmedKey() {
        assertThat(RpcEndpointResolver.buildUrl("https://rpc.example/v2/", "  abc ")).isEqualTo("https://rpc.example/v2/abc");
    }
}
