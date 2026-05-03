package com.codereviewer.service;

import com.codereviewer.model.GitHubReviewComment;
import com.codereviewer.model.ReviewReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewPublisherService {

    private final GitHubApiService gitHubApiService;

    public void publish(String owner, String repo, int prNumber, ReviewReport report) {
        String reviewBody = buildSummaryBody(report);
        List<GitHubReviewComment> inlineComments = buildInlineComments(report);
        gitHubApiService.postReview(owner, repo, prNumber, reviewBody, inlineComments);
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
