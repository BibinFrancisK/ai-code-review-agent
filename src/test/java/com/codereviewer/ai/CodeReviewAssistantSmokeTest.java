package com.codereviewer.ai;

import com.codereviewer.model.ReviewComment;
import com.codereviewer.model.ReviewOutput;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the LLM integration.
 * See src/test/README.md for setup and run instructions.
 */
@Disabled("Run on a need-basis")
@SpringBootTest
@ActiveProfiles("local")
@Slf4j
class CodeReviewAssistantSmokeTest {

    private static final List<String> VALID_RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private static final String CLEAN_PATCH =
            """
            +public class MathUtils {
            +
            +    public int add(int a, int b) {
            +        return a + b;
            +    }
            +}
            """;

    private static final String SQL_INJECTION_PATCH =
            """
            +public class UserRepository {
            +
            +    public User findByUsername(String username) {
            +        String query = "SELECT * FROM users WHERE username = '" + username + "'";
            +        return jdbcTemplate.queryForObject(query, User.class);
            +    }
            +}
            """;

    private static final String NULL_CHECK_PATCH =
            """
            +public class UserService {
            +
            +    private UserRepository userRepository;
            +
            +    public String getUserEmail(Long userId) {
            +        User user = userRepository.findById(userId);
            +        return user.getEmail();
            +    }
            +}
            """;

    @Autowired
    private CodeReviewAssistant codeReviewAssistant;

    @Test
    void smokeTest_cleanPatch_returnsPositiveReview() {
        ReviewOutput result = codeReviewAssistant.reviewPatch(buildPrompt("MathUtils.java", CLEAN_PATCH));

        assertThat(result).isNotNull();
        assertThat(result.summary()).isNotBlank();
        assertThat(result.overallRisk()).isIn(VALID_RISK_LEVELS);
        assertThat(result.comments()).isNotNull();
    }

    @Test
    void smokeTest_sqlInjectionPatch_identifiesSecurityIssue() {
        ReviewOutput result = codeReviewAssistant.reviewPatch(buildPrompt("UserRepository.java", SQL_INJECTION_PATCH));

        assertThat(result).isNotNull();
        assertThat(result.summary()).isNotBlank();
        assertThat(result.overallRisk()).isIn(VALID_RISK_LEVELS);
        assertThat(result.comments()).isNotEmpty();

        ReviewComment comment = result.comments().get(0);
        assertThat(comment.message()).isNotBlank();
        assertThat(comment.suggestion()).isNotBlank();
        assertThat(result.comments())
                .anyMatch(c -> "SECURITY".equals(c.category()));
    }

    @Test
    void smokeTest_nullCheckPatch_identifiesBug() {
        ReviewOutput result = codeReviewAssistant.reviewPatch(buildPrompt("UserService.java", NULL_CHECK_PATCH));

        assertThat(result).isNotNull();
        assertThat(result.summary()).isNotBlank();
        assertThat(result.overallRisk()).isIn(VALID_RISK_LEVELS);
        assertThat(result.comments()).isNotEmpty();

        ReviewComment comment = result.comments().get(0);
        assertThat(comment.message()).isNotBlank();
        assertThat(comment.suggestion()).isNotBlank();
        assertThat(result.comments())
                .anyMatch(c -> "BUG".equals(c.category()));
    }

    private String buildPrompt(String filename, String patch) {
        return ReviewPrompts.USER_PROMPT_TEMPLATE
                .replace("{filename}", filename)
                .replace("{language}", "Java")
                .replace("{additions}", String.valueOf(patch.lines().filter(l -> l.startsWith("+")).count()))
                .replace("{deletions}", "0")
                .replace("{diff_chunk}", patch);
    }
}
