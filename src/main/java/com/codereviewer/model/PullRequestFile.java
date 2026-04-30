package com.codereviewer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PullRequestFile(
        @JsonProperty("filename") String filename,
        @JsonProperty("patch") String patch,
        @JsonProperty("status") String status,
        @JsonProperty("additions") int additions,
        @JsonProperty("deletions") int deletions
) {}
