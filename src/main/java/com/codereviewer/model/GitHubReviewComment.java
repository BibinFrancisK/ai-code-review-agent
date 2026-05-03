package com.codereviewer.model;

// Outgoing payload for a single inline comment in the GitHub PR Review API.
public record GitHubReviewComment(String path, int line, String body) {}
