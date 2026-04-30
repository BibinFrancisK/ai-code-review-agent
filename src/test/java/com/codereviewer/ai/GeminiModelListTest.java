package com.codereviewer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists all Gemini models available for your API key that support generateContent.
 * Run once to find available models when hitting quota limits.
 * Before running locally:
 * 1. Start PostgreSQL:  docker compose up -d
 * 2. Set the LLM API key in your environment or application-local.yml
 * 3. Enable the test
 */
@Disabled("Run on a need-basis")
@SpringBootTest
@ActiveProfiles("local")
@Slf4j
class GeminiModelListTest {

    private static final String MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    @Value("${google.gemini.api-key}")
    private String apiKey;

    @Test
    void listAvailableModels() throws Exception {
        String json = RestClient.create()
                .get()
                .uri(MODELS_URL + "?key=" + apiKey)
                .retrieve()
                .body(String.class);

        JsonNode models = new ObjectMapper().readTree(json).path("models");

        List<String> availableModels = new ArrayList<>();
        log.info("Available Gemini models supporting generateContent:");
        for (JsonNode model : models) {
            boolean supportsGenerate = false;
            for (JsonNode method : model.path("supportedGenerationMethods")) {
                if ("generateContent".equals(method.asText())) {
                    supportsGenerate = true;
                    break;
                }
            }
            if (supportsGenerate) {
                String name = model.path("name").asText().replace("models/", "");
                String displayName = model.path("displayName").asText();
                availableModels.add(name + " : " + displayName + "\n");
            }
        }
        log.info(availableModels.toString().replace(",", ""));
    }
}
