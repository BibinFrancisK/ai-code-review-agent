package com.codereviewer.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class GitHubConfig {

    @Value("${github.token}")
    private String token;

    @Value("${github.webhook.secret}")
    private String webhookSecret;
}
