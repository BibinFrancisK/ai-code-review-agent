package com.codereviewer.controller;

import com.codereviewer.model.PullRequestEvent;
import com.codereviewer.service.PullRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private static final String GITHUB_EVENT_HEADER = "X-GitHub-Event";
    private static final String PULL_REQUEST_EVENT  = "pull_request";

    private final PullRequestService pullRequestService;

    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubWebhook(
            @RequestHeader(value = GITHUB_EVENT_HEADER, required = false) String eventType,
            @RequestBody PullRequestEvent event) {

        if (!PULL_REQUEST_EVENT.equals(eventType)) {
            log.info("Ignoring GitHub event type '{}' — only '{}' is processed", eventType, PULL_REQUEST_EVENT);
            return ResponseEntity.accepted().build();
        }

        long start = System.nanoTime();
        pullRequestService.handlePullRequestEvent(event);
        long dispatchMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Webhook dispatched async processing in {}ms — returning 202.", dispatchMs);
        return ResponseEntity.accepted().build();
    }
}
