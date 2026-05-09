package com.codereviewer.security;

import com.codereviewer.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.codereviewer.util.Constants.RATE_LIMIT_REQUESTS_PER_MINUTE;
import static com.codereviewer.util.Constants.WEBHOOK_PATH;
import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
    }

    @Test
    void nonWebhookPath_skipsFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reviews");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void firstRequest_passesThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(webhookRequest("1.2.3.4"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void exhaustedBucket_returns429WithRetryAfterHeader() throws Exception {
        for (int i = 0; i < RATE_LIMIT_REQUESTS_PER_MINUTE; i++) {
            filter.doFilter(webhookRequest("10.0.0.1"), new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(webhookRequest("10.0.0.1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(Constants.HEADER_RETRY_AFTER)).isEqualTo("60");
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("rate limit exceeded");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void xForwardedFor_usedAsClientIp() throws Exception {
        // Exhaust the bucket keyed to 5.5.5.5 via X-Forwarded-For
        for (int i = 0; i < RATE_LIMIT_REQUESTS_PER_MINUTE; i++) {
            MockHttpServletRequest req = webhookRequest("5.5.5.5");
            req.addHeader(Constants.HEADER_X_FORWARDED_FOR, "5.5.5.5");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // Different remoteAddr but same X-Forwarded-For → same bucket → rate limited
        MockHttpServletRequest request = webhookRequest("192.168.0.1");
        request.addHeader(Constants.HEADER_X_FORWARDED_FOR, "5.5.5.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void xForwardedFor_firstIpUsedWhenMultiplePresent() throws Exception {
        // Exhaust bucket for 3.3.3.3
        for (int i = 0; i < RATE_LIMIT_REQUESTS_PER_MINUTE; i++) {
            MockHttpServletRequest req = webhookRequest("3.3.3.3");
            req.addHeader(Constants.HEADER_X_FORWARDED_FOR, "3.3.3.3");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // X-Forwarded-For: 3.3.3.3, 9.9.9.9 → first IP (3.3.3.3) is used → rate limited
        MockHttpServletRequest request = webhookRequest("9.9.9.9");
        request.addHeader(Constants.HEADER_X_FORWARDED_FOR, "3.3.3.3, 9.9.9.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void differentIps_haveSeparateBuckets() throws Exception {
        // Exhaust IP A's bucket
        for (int i = 0; i < RATE_LIMIT_REQUESTS_PER_MINUTE; i++) {
            filter.doFilter(webhookRequest("100.0.0.1"), new MockHttpServletResponse(), new MockFilterChain());
        }

        // IP B still has its own full bucket
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(webhookRequest("100.0.0.2"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    private static MockHttpServletRequest webhookRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", WEBHOOK_PATH);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
