package com.codereviewer.security;

import com.codereviewer.config.GitHubConfig;
import com.codereviewer.util.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Component
@Order(1) // Sets the priority of this filter relative to other filters in the chain. Lower number = higher priority = runs first.
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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String signatureHeader = request.getHeader(Constants.GITHUB_SIGNATURE_HEADER);
        if (signatureHeader == null) { //header is missing
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing signature header");
            return;
        }

        byte[] rawBody = request.getInputStream().readAllBytes(); //drains the input stream into a byte[], but we have ReReadableRequestWrapper

        //Compute HMAC-SHA256, compare against header.
        try {
            Mac mac = Mac.getInstance(Constants.HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    gitHubConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8), Constants.HMAC_ALGORITHM));
            String computed = Constants.SIGNATURE_PREFIX + HexFormat.of().formatHex(mac.doFinal(rawBody));

            if (!MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");
                return;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Signature verification failed");
            return;
        }

        filterChain.doFilter(new ReReadableRequestWrapper(request, rawBody), response); //pass the wrapped request to the next filter
    }
}
