package com.example.walletbot.platform.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimiterTest {

    private MockHttpServletRequest req(String remoteAddr, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remoteAddr);
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        return r;
    }

    @Test
    void isAllowed_fiveRequestsPass_sixthBlocked() {
        AuthRateLimiter limiter = new AuthRateLimiter(0);
        MockHttpServletRequest request = req("1.2.3.4", null);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.isAllowed(request)).as("request %d", i).isTrue();
        }
        assertThat(limiter.isAllowed(request)).isFalse();
    }

    @Test
    void isAllowed_distinctIpsHaveSeparateBuckets() {
        AuthRateLimiter limiter = new AuthRateLimiter(0);

        for (int i = 0; i < 5; i++) limiter.isAllowed(req("1.1.1.1", null));

        assertThat(limiter.isAllowed(req("2.2.2.2", null))).isTrue();
    }

    @Test
    void isAllowed_noTrustedProxy_ignoresXffUsesRemoteAddr() {
        AuthRateLimiter limiter = new AuthRateLimiter(0);

        for (int i = 0; i < 5; i++) limiter.isAllowed(req("9.9.9.9", "8.8.8." + i));

        assertThat(limiter.isAllowed(req("9.9.9.9", "8.8.8.99"))).isFalse();
    }

    @Test
    void isAllowed_withTrustedProxy_usesEntryFromXffByProxyCount() {
        AuthRateLimiter limiter = new AuthRateLimiter(1);

        for (int i = 0; i < 5; i++) limiter.isAllowed(req("10.0.0.1", "5.5.5.5, 7.7.7.7"));

        assertThat(limiter.isAllowed(req("10.0.0.1", "5.5.5.5, 7.7.7.7"))).isFalse();
        assertThat(limiter.isAllowed(req("10.0.0.1", "5.5.5.5, 8.8.8.8"))).isTrue();
    }
}
