package com.codereviewer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PullRequestInfo(
        @JsonProperty("number") int number,
        @JsonProperty("title") String title,
        @JsonProperty("state") String state,
        @JsonProperty("head") Commit head,
        @JsonProperty("base") Commit base
) {
    public record Commit(@JsonProperty("sha") String sha) {}

    public String headSha() { return head.sha(); }
    public String baseSha() { return base.sha(); }
}
