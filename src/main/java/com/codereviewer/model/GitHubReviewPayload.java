package com.codereviewer.model;

import java.util.List;

// Request body for the GitHub PR Review API (POST /repos/{owner}/{repo}/pulls/{prNumber}/reviews).
public record GitHubReviewPayload(String event, String body, List<GitHubReviewComment> comments) {}
