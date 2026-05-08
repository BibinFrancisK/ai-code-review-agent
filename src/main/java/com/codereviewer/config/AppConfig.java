package com.codereviewer.config;

import com.codereviewer.util.Constants;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AppConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("pr-review-");
        executor.initialize();
        return executor;
    }

    @Bean
    public RestClientCustomizer githubRestClientTimeouts() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofSeconds(30));
            builder.requestFactory(factory);
        };
    }

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
