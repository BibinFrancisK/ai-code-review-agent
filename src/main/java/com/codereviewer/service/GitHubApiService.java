package com.codereviewer.service;

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

@Slf4j //generates a static final log instance
@Service
@RequiredArgsConstructor //generates a constructor that takes all final fields as parameter
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

    private void logRateLimit(ResponseEntity<?> response) {
        String remaining = response.getHeaders().getFirst(Constants.GITHUB_RATE_LIMIT_HEADER);
        if (remaining != null) {
            log.debug("GitHub rate limit remaining: {}", remaining);
        }
    }
}
