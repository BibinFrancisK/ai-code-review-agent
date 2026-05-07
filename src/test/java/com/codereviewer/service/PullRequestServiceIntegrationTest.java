package com.codereviewer.service;

import com.codereviewer.model.DiffChunk;
import com.codereviewer.model.PullRequestEvent;
import com.codereviewer.model.PullRequestFile;
import com.codereviewer.model.ReviewComment;
import com.codereviewer.model.ReviewReport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * End-to-end integration test for the full PR review pipeline.
 * See src/test/README.md for setup and run instructions.
 */
@Disabled("Run on a need-basis")
@SpringBootTest
@ActiveProfiles("local")
@Slf4j
class PullRequestServiceIntegrationTest {

    private static final String OWNER = "BibinFrancisK";
    private static final String REPO = "ai-cloud-architecture-advisor";
    private static final int PR_NUMBER = 4;

    @Autowired
    private GitHubApiService gitHubApiService;

    @Autowired
    private DiffChunkerService diffChunkerService;

    @Autowired
    private LLMReviewService llmReviewService;

    @Autowired
    private PullRequestService pullRequestService;

    @Test
    void pipeline_producesCommentsForRealPr() {
        List<PullRequestFile> files = gitHubApiService.getFiles(OWNER, REPO, PR_NUMBER);
        List<DiffChunk> chunks = diffChunkerService.chunk(files);

        log.info("PR #{}: {} file(s) → {} chunk(s)", PR_NUMBER, files.size(), chunks.size());

        ReviewReport report = llmReviewService.review(PR_NUMBER, chunks);

        logReport(report);

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
                new PullRequestEvent.PullRequestPayload(PR_NUMBER, "Prompt Engineering", "closed"),
                new PullRequestEvent.Repository(OWNER + "/" + REPO)
        );

        assertThatNoException().isThrownBy(() ->
                pullRequestService.handlePullRequestEvent(event).get(60, TimeUnit.SECONDS)
        );
    }

    private void logReport(ReviewReport report) {
        log.info("PR #{}: {} comment(s) across {} file(s)",
                report.getPrNumber(), report.totalComments(), report.getCommentsByFile().size());

        report.getCommentsByFile().forEach((filename, comments) ->
                comments.forEach(c -> log.info("  [{}] {}:{} — {}", c.severity(), filename, c.line(), c.message()))
        );
    }
}
