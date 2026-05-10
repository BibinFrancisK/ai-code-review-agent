package com.codereviewer.ai;

import java.util.Map;

public final class ReviewPrompts {

    private ReviewPrompts() {}

    private static final Map<String, String> LANGUAGE_EXPERTISE = Map.ofEntries(
            Map.entry("java",       "Java and Spring Boot, with focus on JVM performance and enterprise patterns"),
            Map.entry("kotlin",     "Kotlin and JVM development, including coroutines and Android patterns"),
            Map.entry("scala",      "Scala and functional JVM development"),
            Map.entry("python",     "Python, with familiarity with Django, FastAPI, and Pythonic idioms"),
            Map.entry("javascript", "JavaScript and Node.js, including async patterns and the npm ecosystem"),
            Map.entry("typescript", "TypeScript, React, and Node.js, with strong focus on type safety"),
            Map.entry("go",         "Go, with focus on idiomatic Go, goroutines, and error handling"),
            Map.entry("rust",       "Rust, with deep focus on ownership, borrowing, and memory safety"),
            Map.entry("csharp",     "C# and .NET, including ASP.NET Core and LINQ patterns"),
            Map.entry("ruby",       "Ruby and Rails, with focus on idiomatic Ruby and MVC patterns"),
            Map.entry("php",        "PHP and modern frameworks like Laravel and Symfony"),
            Map.entry("swift",      "Swift and iOS/macOS development, including UIKit and SwiftUI"),
            Map.entry("sql",        "SQL and relational database design, with focus on query optimisation"),
            Map.entry("terraform",  "Terraform and infrastructure-as-code, with cloud provider best practices"),
            Map.entry("dockerfile", "Docker and container best practices, including layer optimisation and security")
    );

    public static String expertiseFor(String language) {
        return LANGUAGE_EXPERTISE.getOrDefault(language, language + " development best practices");
    }

    public static final String SYSTEM_PROMPT =
            """
            You are an expert code reviewer specialising in {{expertise}},
            with deep knowledge of security (OWASP Top 10), performance, and clean architecture.

            Review the provided code diff and identify issues.

            CATEGORIES:
            - BUG: Logic errors, null pointer risks, race conditions, off-by-one
            - SECURITY: SQL injection, hardcoded secrets, XXE, insecure deserialization,
              OWASP Top 10, insufficient input validation
            - PERFORMANCE: N+1 queries, O(n²) where O(n) possible, unnecessary boxing,
              missing caching, synchronous calls that should be async
            - QUALITY: Swallowed exceptions, missing null checks, code duplication,
              SOLID violations, unclear variable names

            SEVERITY:
            - CRITICAL: Block merge (data corruption, active security vulnerability)
            - HIGH: Fix before merge (significant bug, major security risk)
            - MEDIUM: Fix soon (code quality, minor bug)
            - LOW: Consider improving (style, minor inefficiency)

            Return ONLY valid JSON. No markdown. No text outside the JSON object.

            {
              "summary": "One sentence assessment",
              "overallRisk": "LOW|MEDIUM|HIGH|CRITICAL",
              "comments": [
                {
                  "line": <the [N] number shown at the start of the diff line being commented on>,
                  "severity": "CRITICAL|HIGH|MEDIUM|LOW",
                  "category": "BUG|SECURITY|PERFORMANCE|QUALITY",
                  "message": "What the issue is (1-2 sentences)",
                  "suggestion": "How to fix it — include corrected code snippet if helpful"
                }
              ]
            }

            If no issues: {"summary": "Code looks good", "overallRisk": "LOW", "comments": []}
            """;

    public static final String USER_PROMPT_TEMPLATE =
            """
            File: {filename}
            Language: {language}
            Lines changed: +{additions} -{deletions}

            Diff (added and context lines are prefixed with [N] — use that N as the "line" value):
            {diff_chunk}

            Review this diff. Focus only on the changed lines and their immediate context.
            """;
}
