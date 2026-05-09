package com.codereviewer.config;

import lombok.Getter;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class GitHubConfig {

    @ToString.Exclude
    @Value("${github.token}")
    private String token;

    @ToString.Exclude
    @Value("${github.webhook.secret}")
    private String webhookSecret;
}
