package com.codereviewer.security;

import com.codereviewer.config.GitHubConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookSignatureFilterTest {

    private static final String TEST_SECRET = "test-secret";
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String WEBHOOK_URI = "/webhook/github";

    @Mock
    private GitHubConfig gitHubConfig;

    private WebhookSignatureFilter filter;

    @BeforeEach
    void setUp() {
        when(gitHubConfig.getWebhookSecret()).thenReturn(TEST_SECRET);
        filter = new WebhookSignatureFilter(gitHubConfig);
    }

    @Test
    void validSignature_requestPassesThrough() throws Exception {
        String body = "{\"action\":\"opened\"}";
        MockHttpServletRequest request = webhookRequest(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader(SIGNATURE_HEADER, sign(TEST_SECRET, body));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    void tamperedBody_returns401() throws Exception {
        String originalBody = "{\"action\":\"opened\"}";
        byte[] tamperedBody = "{\"action\":\"tampered\"}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest request = webhookRequest(tamperedBody);
        request.addHeader(SIGNATURE_HEADER, sign(TEST_SECRET, originalBody));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void missingSignatureHeader_returns401() throws Exception {
        MockHttpServletRequest request = webhookRequest("{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void wrongAlgorithmPrefix_returns401() throws Exception {
        String body = "{\"action\":\"opened\"}";
        // Compute a correct SHA-256 HMAC then swap the "sha256=" prefix for "sha1="
        String sha1PrefixedSignature = "sha1=" + sign(TEST_SECRET, body).substring("sha256=".length());

        MockHttpServletRequest request = webhookRequest(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader(SIGNATURE_HEADER, sha1PrefixedSignature);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    private static MockHttpServletRequest webhookRequest(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", WEBHOOK_URI);
        request.setContent(body);
        return request;
    }

    private static String sign(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
