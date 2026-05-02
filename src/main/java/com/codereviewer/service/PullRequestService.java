package com.codereviewer.service;

import com.codereviewer.model.DiffChunk;
import com.codereviewer.model.PullRequestEvent;
import com.codereviewer.model.PullRequestFile;
import com.codereviewer.model.ReviewComment;
import com.codereviewer.model.ReviewReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.codereviewer.util.Constants;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PullRequestService {

    private final GitHubApiService gitHubApiService;
    private final DiffChunkerService diffChunkerService;
    private final LLMReviewService llmReviewService;

    @Async
    public CompletableFuture<Void> handlePullRequestEvent(PullRequestEvent event) {
        if (!Constants.PROCESSED_ACTIONS.contains(event.action())) {
            log.debug("Ignoring PR event with action '{}'", event.action());
            return CompletableFuture.completedFuture(null);
        }

        int prNumber = event.pullRequest().number();
        String fullName = event.repository().fullName();
        String[] parts = fullName.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        log.info("Processing PR #{} ({}) — action: {}", prNumber, fullName, event.action());

        List<PullRequestFile> files = gitHubApiService.getFiles(owner, repo, prNumber);
        List<DiffChunk> chunks = diffChunkerService.chunk(files);

        log.info("PR #{}: {} files → {} chunks", prNumber, files.size(), chunks.size());

        ReviewReport report = llmReviewService.review(prNumber, chunks);

        logReport(report);

        return CompletableFuture.completedFuture(null);
    }

    // Temporary: replaced by ReviewPublisherService inline comments in a future day
    private void logReport(ReviewReport report) {
        log.info("PR #{}: {} comment(s) across {} file(s)",
                report.getPrNumber(), report.totalComments(), report.getCommentsByFile().size());

        for (Map.Entry<String, List<ReviewComment>> entry : report.getCommentsByFile().entrySet()) {
            for (ReviewComment comment : entry.getValue()) {
                log.info("  [{}] {}:{} — {}",
                        comment.severity(), entry.getKey(), comment.line(), comment.message());
            }
        }
    }
}
