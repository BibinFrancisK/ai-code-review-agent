package com.codereviewer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PullRequestEvent(
        @JsonProperty("action") String action,
        @JsonProperty("pull_request") PullRequestPayload pullRequest,
        @JsonProperty("repository") Repository repository
) {
    public record PullRequestPayload(
            @JsonProperty("number") int number,
            @JsonProperty("title") String title,
            @JsonProperty("state") String state
    ) {}

    public record Repository(
            @JsonProperty("full_name") String fullName
    ) {}
}
