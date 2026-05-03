package com.codereviewer.service;

import com.codereviewer.entity.ReviewCommentEntity;
import com.codereviewer.entity.ReviewEntity;
import com.codereviewer.model.DiffChunk;
import com.codereviewer.model.PullRequestEvent;
import com.codereviewer.model.PullRequestFile;
import com.codereviewer.model.ReviewComment;
import com.codereviewer.model.ReviewReport;
import com.codereviewer.repository.ReviewRepository;
import com.codereviewer.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final ReviewPublisherService reviewPublisherService;
    private final ReviewRepository reviewRepository;

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

        try {
            reviewPublisherService.publish(owner, repo, prNumber, report);
        } catch (Exception e) {
            log.error("PR #{}: failed to post review to GitHub — {}", prNumber, e.getMessage(), e);
        }

        persistReview(event, owner, repo, prNumber, report);

        return CompletableFuture.completedFuture(null);
    }

    private void persistReview(PullRequestEvent event, String owner, String repo,
                               int prNumber, ReviewReport report) {
        try {
            ReviewEntity entity = ReviewEntity.builder()
                    .owner(owner)
                    .repo(repo)
                    .prNumber(prNumber)
                    .prTitle(event.pullRequest().title())
                    .overallRisk(report.deriveOverallRisk())
                    .commentCount(report.totalComments())
                    .createdAt(LocalDateTime.now())
                    .build();

            List<ReviewCommentEntity> commentEntities = report.getCommentsByFile().entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(c -> ReviewCommentEntity.builder()
                                    .review(entity)
                                    .filename(entry.getKey())
                                    .line(c.line())
                                    .severity(c.severity())
                                    .category(c.category())
                                    .message(c.message())
                                    .suggestion(c.suggestion())
                                    .build()))
                    .toList();

            entity.setComments(commentEntities);
            reviewRepository.save(entity);

            log.info("PR #{}: persisted review ID = {} with {} comment(s)",
                    prNumber, entity.getId(), commentEntities.size());
        } catch (Exception e) {
            log.error("PR #{}: failed to persist review — {}", prNumber, e.getMessage(), e);
        }
    }

    private void logReport(ReviewReport report) {
        log.debug("PR #{}: {} comment(s) across {} file(s)",
                report.getPrNumber(), report.totalComments(), report.getCommentsByFile().size());

        for (Map.Entry<String, List<ReviewComment>> entry : report.getCommentsByFile().entrySet()) {
            for (ReviewComment comment : entry.getValue()) {
                log.debug("  [{}] {}:{} — {}",
                        comment.severity(), entry.getKey(), comment.line(), comment.message());
            }
        }
    }
}
