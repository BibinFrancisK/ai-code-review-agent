package com.codereviewer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubWebhook(@RequestBody(required = false) String payload) {
        return ResponseEntity.ok().build();
    }
}
