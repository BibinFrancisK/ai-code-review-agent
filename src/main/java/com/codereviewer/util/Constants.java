package com.codereviewer.util;

public final class Constants {

    private Constants() {}

    // GitHub API
    public static final String GITHUB_API_BASE_URL = "https://api.github.com";
    public static final String GITHUB_ACCEPT_HEADER = "application/vnd.github+json";
    public static final String GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version";
    public static final String GITHUB_API_VERSION = "2022-11-28";
    public static final String GITHUB_RATE_LIMIT_HEADER = "X-RateLimit-Remaining";
    public static final String GITHUB_PR_FILES_PATH = "/repos/{owner}/{repo}/pulls/{prNumber}/files";
    public static final String GITHUB_PR_INFO_PATH = "/repos/{owner}/{repo}/pulls/{prNumber}";

    // HTTP / Auth
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String BEARER_PREFIX = "Bearer ";

    // Webhook
    public static final String GITHUB_SIGNATURE_HEADER = "X-Hub-Signature-256";
    public static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final String SIGNATURE_PREFIX = "sha256=";
    public static final String WEBHOOK_PATH = "/webhook/github";

}
