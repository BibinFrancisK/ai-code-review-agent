package com.codereviewer.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test-error")
        void throwWithMessage() { throw new RuntimeException("something broke"); }

        @GetMapping("/test-error-no-message")
        void throwNoMessage() { throw new NullPointerException(); }

        @PostMapping("/webhook/github")
        void webhookThrow() { throw new RuntimeException("webhook error"); }
    }

    @Test
    void nonWebhookException_returns500WithErrorBody() throws Exception {
        mockMvc.perform(get("/test-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("something broke"));
    }

    @Test
    void webhookPathException_returns200ToAllowGitHubRetry() throws Exception {
        mockMvc.perform(post("/webhook/github"))
                .andExpect(status().isOk());
    }

    @Test
    void exceptionWithNullMessage_usesClassNameAsFallback() throws Exception {
        mockMvc.perform(get("/test-error-no-message"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("java.lang.NullPointerException"));
    }
}
