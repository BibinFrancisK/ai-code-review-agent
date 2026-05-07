package com.codereviewer.util;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class Constants {

    private Constants() {}

    // DiffChunkerService
    public static final int MAX_CHUNK_LINES = 150;

    public static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@"
    );

    public static final Set<String> SKIPPED_FILENAMES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "go.sum", "cargo.lock"
    );

    public static final Set<String> SKIPPED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "ico", "svg", "webp",
            "class", "jar", "war", "ear",
            "zip", "tar", "gz", "7z"
    );

    public static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
            Map.entry("java", "java"),
            Map.entry("kt", "kotlin"),
            Map.entry("kts", "kotlin"),
            Map.entry("scala", "scala"),
            Map.entry("groovy", "groovy"),
            Map.entry("py", "python"),
            Map.entry("js", "javascript"),
            Map.entry("mjs", "javascript"),
            Map.entry("cjs", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("tsx", "typescript"),
            Map.entry("jsx", "javascript"),
            Map.entry("go", "go"),
            Map.entry("rs", "rust"),
            Map.entry("c", "c"),
            Map.entry("cpp", "cpp"),
            Map.entry("cs", "csharp"),
            Map.entry("rb", "ruby"),
            Map.entry("php", "php"),
            Map.entry("swift", "swift"),
            Map.entry("html", "html"),
            Map.entry("css", "css"),
            Map.entry("scss", "scss"),
            Map.entry("less", "less"),
            Map.entry("xml", "xml"),
            Map.entry("json", "json"),
            Map.entry("yaml", "yaml"),
            Map.entry("yml", "yaml"),
            Map.entry("toml", "toml"),
            Map.entry("sh", "bash"),
            Map.entry("bash", "bash"),
            Map.entry("sql", "sql"),
            Map.entry("md", "markdown"),
            Map.entry("tf", "terraform"),
            Map.entry("dockerfile", "dockerfile")
    );

    // Review severities (ordered highest → lowest)
    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_HIGH     = "HIGH";
    public static final String SEVERITY_MEDIUM   = "MEDIUM";
    public static final String SEVERITY_LOW      = "LOW";

    // PullRequestService
    public static final Set<String> PROCESSED_ACTIONS = Set.of("opened", "synchronize");

    // GitHub API
    public static final String GITHUB_API_BASE_URL = "https://api.github.com";
    public static final String GITHUB_ACCEPT_HEADER = "application/vnd.github+json";
    public static final String GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version";
    public static final String GITHUB_API_VERSION = "2022-11-28";
    public static final String GITHUB_RATE_LIMIT_HEADER = "X-RateLimit-Remaining";
    public static final String GITHUB_PR_FILES_PATH    = "/repos/{owner}/{repo}/pulls/{prNumber}/files";
    public static final String GITHUB_PR_INFO_PATH     = "/repos/{owner}/{repo}/pulls/{prNumber}";
    public static final String GITHUB_PR_REVIEWS_PATH  = "/repos/{owner}/{repo}/pulls/{prNumber}/reviews";
    public static final String GITHUB_ISSUE_COMMENTS_PATH = "/repos/{owner}/{repo}/issues/{prNumber}/comments";

    // HTTP / Auth
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String BEARER_PREFIX = "Bearer ";

    // Webhook
    public static final String GITHUB_SIGNATURE_HEADER = "X-Hub-Signature-256";
    public static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final String SIGNATURE_PREFIX = "sha256=";
    public static final String WEBHOOK_PATH = "/webhook/github";

    // GitHub user introspection
    public static final String GITHUB_USER_URL         = GITHUB_API_BASE_URL + "/user";
    public static final String GITHUB_USER_LOGIN_FIELD = "login";
    public static final String GITHUB_USER_ID_FIELD    = "id";
    public static final String GITHUB_SCOPE_READ_USER  = "SCOPE_read:user";

    // Security — endpoint matchers
    public static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    public static final String ACTUATOR_INFO_PATH   = "/actuator/info";
    public static final String ACTUATOR_ALL_PATH    = "/actuator/**";
    public static final String API_REVIEWS_PATH     = "/api/reviews/**";

    // Springdoc / Swagger UI — permit unauthenticated access
    public static final String SWAGGER_UI_PATH      = "/swagger-ui/**";
    public static final String SWAGGER_UI_HTML      = "/swagger-ui.html";
    public static final String OPENAPI_DOCS_PATH    = "/v3/api-docs/**";

}
