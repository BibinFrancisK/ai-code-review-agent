package com.codereviewer.config;

import com.codereviewer.util.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    public RestClient githubRestClient(RestClient.Builder builder, GitHubConfig gitHubConfig) {
        return builder
                .baseUrl(Constants.GITHUB_API_BASE_URL)
                .defaultHeader(Constants.HEADER_AUTHORIZATION, Constants.BEARER_PREFIX + gitHubConfig.getToken())
                .defaultHeader(Constants.HEADER_ACCEPT, Constants.GITHUB_ACCEPT_HEADER)
                .defaultHeader(Constants.GITHUB_API_VERSION_HEADER, Constants.GITHUB_API_VERSION)
                .build();
    }
}
