package com.codereviewer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewComment(
        @JsonProperty("line") int line,
        @JsonProperty("severity") String severity,
        @JsonProperty("category") String category,
        @JsonProperty("message") String message,
        @JsonProperty("suggestion") String suggestion
) {
    public ReviewComment {
        severity = severity != null ? severity.toUpperCase() : null;
        category = category != null ? category.toUpperCase() : null;
    }
}
