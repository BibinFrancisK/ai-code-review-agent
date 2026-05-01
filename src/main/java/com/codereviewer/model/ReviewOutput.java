package com.codereviewer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ReviewOutput(
        @JsonProperty("summary") String summary,
        @JsonProperty("overallRisk") String overallRisk,
        @JsonProperty("comments") List<ReviewComment> comments
) {
    public ReviewOutput {
        overallRisk = overallRisk != null ? overallRisk.toUpperCase() : null;
    }
}
