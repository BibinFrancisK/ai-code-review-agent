package com.codereviewer.config;

import com.codereviewer.util.Constants;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${openrouter.api-key}")
    private String openRouterApiKey;

    @Value("${openrouter.model}")
    private String openRouterModel;

    @Bean
    public OpenAiChatModel chatModel() {
        log.info("Initializing OpenRouter chat model ({})", openRouterModel);
        return OpenAiChatModel.builder()
                .baseUrl(Constants.OPENROUTER_BASE_URL)
                .apiKey(openRouterApiKey)
                .modelName(openRouterModel)
                .maxRetries(2)
                .timeout(Duration.ofSeconds(90))
                .build();
    }
}
