package com.codereviewer.service;

import com.codereviewer.model.DiffChunk;
import com.codereviewer.model.PullRequestEvent;
import com.codereviewer.model.PullRequestFile;
import com.codereviewer.model.ReviewComment;
import com.codereviewer.model.ReviewReport;
import com.codereviewer.repository.ReviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "github.webhook.secret=test-secret",
        "github.token=test-token",
        "google.gemini.api-key=test-key",
        "google.gemini.model=test-model"
})
class PullRequestServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // Mock the external dependencies, not the LangChain4j @AiService interface —
    // LangChain4j's factory cannot proxy a Mockito subclass.
    @MockitoBean
    private GitHubApiService gitHubApiService;

    @MockitoBean
    private LLMReviewService llmReviewService;

    @Autowired
    private DiffChunkerService diffChunkerService;

    @Autowired
    private PullRequestService pullRequestService;

    @Autowired
    private ReviewRepository reviewRepository;

    private static final String OWNER = "test-owner";
    private static final String REPO  = "test-repo";
    private static final int    PR    = 1;

    @BeforeEach
    void setUp() {
        PullRequestFile file = new PullRequestFile(
                "src/Foo.java",
                "@@ -1,3 +1,5 @@\n context\n+String q = id;\n+return q;",
                "modified", 2, 0);
        when(gitHubApiService.getFiles(any(), any(), anyInt())).thenReturn(List.of(file));

        ReviewReport mockReport = new ReviewReport(PR);
        mockReport.addComments("src/Foo.java",
                List.of(new ReviewComment(2, "HIGH", "SECURITY", "SQL injection risk", "Use parameterized query")));
        when(llmReviewService.review(anyInt(), any())).thenReturn(mockReport);
    }

    @AfterEach
    void cleanDb() {
        reviewRepository.deleteAll();
    }

    @Test
    void pipeline_producesCommentsForRealPr() {
        List<PullRequestFile> files = gitHubApiService.getFiles(OWNER, REPO, PR);
        List<DiffChunk> chunks = diffChunkerService.chunk(files);
        assertThat(chunks).isNotEmpty();

        ReviewReport report = llmReviewService.review(PR, chunks);

        assertThat(report.totalComments()).isGreaterThan(0);
        assertThat(report.getCommentsByFile().values())
                .flatMap(comments -> comments)
                .extracting(ReviewComment::line)
                .allMatch(line -> line > 0);
    }

    @Test
    void asyncWrapper_completesWithoutException() {
        PullRequestEvent event = new PullRequestEvent(
                "opened",
                new PullRequestEvent.PullRequestPayload(PR, "Test PR", "open"),
                new PullRequestEvent.Repository(OWNER + "/" + REPO)
        );

        assertThatNoException().isThrownBy(() ->
                pullRequestService.handlePullRequestEvent(event).get(30, TimeUnit.SECONDS)
        );
    }
}
