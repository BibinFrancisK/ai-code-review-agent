package com.codereviewer.service;

import com.codereviewer.model.GitHubReviewComment;
import com.codereviewer.model.ReviewOutcome;
import com.codereviewer.model.ReviewReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewPublisherService {

    private final GitHubApiService gitHubApiService;

    public ReviewOutcome publish(String owner, String repo, int prNumber, ReviewReport report) {
        String reviewBody = buildSummaryBody(report);
        List<GitHubReviewComment> inlineComments = buildInlineComments(report);
        try {
            gitHubApiService.postReview(owner, repo, prNumber, reviewBody, inlineComments);
            return ReviewOutcome.POSTED;
        } catch (RestClientException e) {
            log.warn("Inline review post failed for {}/{} PR#{}, falling back to summary comment: {}",
                    owner, repo, prNumber, e.getMessage());
            try {
                gitHubApiService.postIssueFallbackComment(owner, repo, prNumber,
                        reviewBody + "\n\n> ⚠️ Inline comments could not be posted — see summary above.");
                return ReviewOutcome.FALLBACK_POSTED;
            } catch (RestClientException fallbackEx) {
                log.error("Fallback comment also failed for {}/{} PR#{}: {}",
                        owner, repo, prNumber, fallbackEx.getMessage());
                return ReviewOutcome.FAILED;
            }
        }
    }

    private String buildSummaryBody(ReviewReport report) {
        return "## AI Code Review\n\n" +
               "**Overall Risk:** " + report.deriveOverallRisk();
    }

    private List<GitHubReviewComment> buildInlineComments(ReviewReport report) {
        return report.getCommentsByFile().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .filter(c -> c.line() > 0)
                        .map(c -> new GitHubReviewComment(
                                entry.getKey(),
                                c.line(),
                                "**[" + c.severity() + " — " + c.category() + "]** " + c.message() +
                                (c.suggestion() != null && !c.suggestion().isBlank()
                                        ? "\n\n**Suggestion:** " + c.suggestion()
                                        : "")
                        )))
                .toList();
    }
}
