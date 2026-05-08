package com.codereviewer.service;

import com.codereviewer.entity.ReviewCommentEntity;
import com.codereviewer.entity.ReviewEntity;
import com.codereviewer.model.DiffChunk;
import com.codereviewer.model.PullRequestEvent;
import com.codereviewer.model.PullRequestFile;
import com.codereviewer.model.ReviewComment;
import com.codereviewer.model.ReviewOutcome;
import com.codereviewer.model.ReviewReport;
import com.codereviewer.repository.ReviewRepository;
import com.codereviewer.util.Constants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
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
    private final MeterRegistry meterRegistry;

    private Timer llmReviewTimer;

    @PostConstruct
    void initMetrics() {
        llmReviewTimer = Timer.builder("llm.review.duration")
                .description("End-to-end LLM review time per PR")
                .register(meterRegistry);
    }

    @Async
    public CompletableFuture<ReviewOutcome> handlePullRequestEvent(PullRequestEvent event) {
        if (!Constants.PROCESSED_ACTIONS.contains(event.action())) {
            log.info("Ignoring PR event with action '{}'", event.action());
            return CompletableFuture.completedFuture(ReviewOutcome.SKIPPED);
        }

        int prNumber = event.pullRequest().number();
        String fullName = event.repository().fullName();
        String[] parts = fullName.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        log.info("repo: {}/{} PR:#{}: processing — action: {}", owner, repo, prNumber, event.action());

        try {
            List<PullRequestFile> files = gitHubApiService.getFiles(owner, repo, prNumber);
            List<DiffChunk> chunks = diffChunkerService.chunk(files);

            log.info("repo: {}/{} PR:#{}: {} files → {} chunks", owner, repo, prNumber, files.size(), chunks.size());

            long reviewStartNs = System.nanoTime();
            ReviewReport report = llmReviewTimer.record(() -> llmReviewService.review(prNumber, chunks));
            long reviewDurationMs = (System.nanoTime() - reviewStartNs) / 1_000_000;
            log.info("repo: {}/{} PR:#{}: LLM review completed in {}ms — {} comment(s)", owner, repo, prNumber, reviewDurationMs, report.totalComments());

            logReport(report);

            ReviewOutcome outcome;
            try {
                outcome = reviewPublisherService.publish(owner, repo, prNumber, report);
            } catch (Exception e) {
                log.error("repo: {}/{} PR:#{}: failed to post review to GitHub — {}", owner, repo, prNumber, e.getMessage(), e);
                outcome = ReviewOutcome.FAILED;
            }

            log.info("repo: {}/{} PR:#{}: review outcome = {}", owner, repo, prNumber, outcome);

            Counter.builder("reviews.processed")
                    .description("Number of PR reviews completed")
                    .tag("outcome", outcome.name())
                    .register(meterRegistry)
                    .increment();

            persistReview(event, owner, repo, prNumber, report);

            return CompletableFuture.completedFuture(outcome);

        } catch (Exception e) {
            log.error("repo: {}/{} PR:#{}: async processing failed — {}", owner, repo, prNumber, e.getMessage(), e);
            return CompletableFuture.completedFuture(ReviewOutcome.FAILED);
        }
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
