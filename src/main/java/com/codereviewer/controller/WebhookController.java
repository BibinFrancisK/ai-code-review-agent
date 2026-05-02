package com.codereviewer.controller;

import com.codereviewer.model.PullRequestEvent;
import com.codereviewer.service.PullRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final PullRequestService pullRequestService;

    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubWebhook(@RequestBody PullRequestEvent event) {
        pullRequestService.handlePullRequestEvent(event);
        return ResponseEntity.accepted().build();
    }
}
