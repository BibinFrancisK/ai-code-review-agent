package com.codereviewer.security;

import com.codereviewer.config.GitHubConfig;
import com.codereviewer.util.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Component
@Order(1)
public class WebhookSignatureFilter extends OncePerRequestFilter { //called at most once per request, even if the request is dispatched multiple times internally.

    private final GitHubConfig gitHubConfig;

    public WebhookSignatureFilter(GitHubConfig gitHubConfig) {
        this.gitHubConfig = gitHubConfig;
    }

    // Requests to /actuator/health, /api/reviews etc. should not be rejected.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !Constants.WEBHOOK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null) {
            try {
                if (Long.parseLong(contentLengthHeader) > Constants.MAX_PAYLOAD_BYTES) {
                    response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Payload too large");
                    return;
                }
            } catch (NumberFormatException nfe) {
                log.warn("Ignoring malformed header as body read enforces cap.");
            }
        }

        String signatureHeader = request.getHeader(Constants.GITHUB_SIGNATURE_HEADER);
        if (signatureHeader == null) {
            log.warn("Webhook rejected: missing {} header", Constants.GITHUB_SIGNATURE_HEADER);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing signature header");
            return;
        }

        byte[] rawBody = request.getInputStream().readNBytes(Constants.MAX_PAYLOAD_BYTES + 1);
        if (rawBody.length > Constants.MAX_PAYLOAD_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Payload too large");
            return;
        }

        try {
            Mac mac = Mac.getInstance(Constants.HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    gitHubConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8), Constants.HMAC_ALGORITHM));
            String computed = Constants.SIGNATURE_PREFIX + HexFormat.of().formatHex(mac.doFinal(rawBody));

            if (!MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Webhook rejected: signature mismatch");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");
                return;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Webhook rejected: HMAC computation failed — {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Signature verification failed");
            return;
        }

        filterChain.doFilter(new ReReadableRequestWrapper(request, rawBody), response); //pass the wrapped request to the next filter
    }
}
