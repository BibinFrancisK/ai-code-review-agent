package com.codereviewer.service;

import com.codereviewer.model.GitHubReviewComment;
import com.codereviewer.model.GitHubReviewPayload;
import com.codereviewer.model.PullRequestFile;
import com.codereviewer.model.PullRequestInfo;
import com.codereviewer.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubApiService {

    private final RestClient githubRestClient;

    public List<PullRequestFile> getFiles(String owner, String repo, int prNumber) {
        ResponseEntity<List<PullRequestFile>> response = githubRestClient.get()
                .uri(Constants.GITHUB_PR_FILES_PATH, owner, repo, prNumber)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {}); //ParameterizedTypeReference is needed since we are using List<T>

        logRateLimit(response);
        return response.getBody();
    }

    public PullRequestInfo getPrInfo(String owner, String repo, int prNumber) {
        ResponseEntity<PullRequestInfo> response = githubRestClient.get()
                .uri(Constants.GITHUB_PR_INFO_PATH, owner, repo, prNumber)
                .retrieve()
                .toEntity(PullRequestInfo.class);

        logRateLimit(response);
        return response.getBody();
    }

    public void postReview(String owner, String repo, int prNumber,
                           String reviewBody, List<GitHubReviewComment> comments) {
        GitHubReviewPayload payload = new GitHubReviewPayload("COMMENT", reviewBody,
                comments.isEmpty() ? null : comments);

        githubRestClient.post()
                .uri(Constants.GITHUB_PR_REVIEWS_PATH, owner, repo, prNumber)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("Posted review to {}/{} PR#{} — {} inline comment(s)",
                owner, repo, prNumber, comments.size());
    }

    public void postIssueFallbackComment(String owner, String repo, int prNumber, String body) {
        githubRestClient.post()
                .uri(Constants.GITHUB_ISSUE_COMMENTS_PATH, owner, repo, prNumber)
                .body(Map.of("body", body))
                .retrieve()
                .toBodilessEntity();

        log.info("Posted fallback summary comment to {}/{} PR#{}", owner, repo, prNumber);
    }

    private void logRateLimit(ResponseEntity<?> response) {
        String remaining = response.getHeaders().getFirst(Constants.GITHUB_RATE_LIMIT_HEADER);
        if (remaining != null) {
            log.debug("GitHub rate limit remaining: {}", remaining);
        }
    }
}
