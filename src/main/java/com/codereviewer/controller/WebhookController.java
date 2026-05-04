package com.codereviewer.controller;

import com.codereviewer.model.PullRequestEvent;
import com.codereviewer.service.PullRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final PullRequestService pullRequestService;

    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubWebhook(@RequestBody PullRequestEvent event) {
        long start = System.nanoTime();
        pullRequestService.handlePullRequestEvent(event);
        long dispatchMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Webhook dispatched async processing in {}ms — returning 202.", dispatchMs);
        return ResponseEntity.accepted().build();
    }
}
