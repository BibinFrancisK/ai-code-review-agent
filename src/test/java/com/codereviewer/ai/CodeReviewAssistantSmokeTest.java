package com.codereviewer.ai;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the LLM integration.
 * Before running locally:
 * 1. Start PostgreSQL:  docker compose up -d
 * 2. Set the LLM API key in your environment or application-local.yml
 * 3. Enable the test
 */
@Disabled("Run on a need-basis")
@SpringBootTest
@ActiveProfiles("local")
@Slf4j
class CodeReviewAssistantSmokeTest {

    private static final String BUGGY_PATCH =
            """
                    +public class UserService {
                    +
                    +    private UserRepository userRepository;
                    +
                    +    public String getUserEmail(Long userId) {
                    +        User user = userRepository.findById(userId);
                    +        return user.getEmail();
                    +    }
                    +
                    +    public void deleteUser(Long userId) {
                    +        userRepository.deleteById(userId);
                    +        System.out.println("Deleted: " + userId);
                    +    }
                    +}
                    """;

    @Autowired
    private CodeReviewAssistant codeReviewAssistant;

    @Test
    void smokeTest_llmIdentifiesNullPointerBug() {
        String prompt = """
                File: UserService.java
                Language: Java
                Lines changed: +14 -0
                
                Diff (line numbers reference the new file version):
                """
                + BUGGY_PATCH +
                """
                Review this diff. Focus only on the changed lines and their immediate context.
                """;

        String result = codeReviewAssistant.reviewPatch(prompt);

        log.info("LLM response:\n{}", result);

        assertThat(result).isNotBlank();
    }
}
