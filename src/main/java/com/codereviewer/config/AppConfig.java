package com.codereviewer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    public RestClient githubRestClient(RestClient.Builder builder, GitHubConfig gitHubConfig) {
        return builder
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + gitHubConfig.getToken())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }
}
