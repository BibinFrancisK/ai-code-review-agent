package com.codereviewer.config;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${google.gemini.api-key}")
    private String geminiApiKey;

    @Value("${google.gemini.model}")
    private String geminiModel;

    @Bean
    public GoogleAiGeminiChatModel chatModel() {
        log.info("Using Gemini model: {}", geminiModel);
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName(geminiModel)
                .maxRetries(2)
                .timeout(Duration.ofSeconds(120))
                .build();
    }
}
