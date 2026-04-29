package com.codereviewer.controller;

import com.codereviewer.config.GitHubConfig;
import com.codereviewer.security.WebhookSignatureFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@Import({GitHubConfig.class, WebhookSignatureFilter.class})
@TestPropertySource(properties = {
        "github.webhook.secret=test-secret",
        "github.token=test-token"
})
class WebhookControllerTest {

    private static final String TEST_SECRET = "test-secret";
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validSignature_returns200() throws Exception {
        String body = "{\"action\":\"opened\"}";

        mockMvc.perform(post("/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, computeSignature(TEST_SECRET, body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void invalidSignature_returns401() throws Exception {
        mockMvc.perform(post("/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, "sha256=badhash")
                        .content("{\"action\":\"opened\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingSignatureHeader_returns401() throws Exception {
        mockMvc.perform(post("/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"opened\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static String computeSignature(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
