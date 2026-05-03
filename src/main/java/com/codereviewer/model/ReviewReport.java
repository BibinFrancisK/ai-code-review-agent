package com.codereviewer.model;

import com.codereviewer.util.Constants;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ReviewReport {

    @Getter
    private final int prNumber;
    private final Map<String, List<ReviewComment>> commentsByFile = new LinkedHashMap<>();

    public ReviewReport(int prNumber) {
        this.prNumber = prNumber;
    }

    public void addComments(String filename, List<ReviewComment> comments) {
        if (comments == null || comments.isEmpty()) return;
        commentsByFile.computeIfAbsent(filename, k -> new ArrayList<>()).addAll(comments);
    }

    public int totalComments() {
        return commentsByFile.values().stream().mapToInt(List::size).sum();
    }

    public Map<String, List<ReviewComment>> getCommentsByFile() {
        return Collections.unmodifiableMap(commentsByFile);
    }

    public String deriveOverallRisk() {
        Set<String> severities = commentsByFile.values().stream()
                .flatMap(List::stream)
                .map(ReviewComment::severity)
                .collect(Collectors.toSet());
        if (severities.contains(Constants.SEVERITY_CRITICAL)) return Constants.SEVERITY_CRITICAL;
        if (severities.contains(Constants.SEVERITY_HIGH))     return Constants.SEVERITY_HIGH;
        if (severities.contains(Constants.SEVERITY_MEDIUM))   return Constants.SEVERITY_MEDIUM;
        return Constants.SEVERITY_LOW;
    }
}
