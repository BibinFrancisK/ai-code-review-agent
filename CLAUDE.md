# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Compile
mvn clean compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=WebhookControllerTest

# Run a single test method
mvn test -Dtest=WebhookControllerTest#methodName

# Package (skip tests)
mvn package -DskipTests

# Run locally (requires PostgreSQL + env vars — see Environment Variables below)
mvn spring-boot:run

# Run LLM smoke test (disabled by default — remove @Disabled first)
mvn test -Dtest=CodeReviewAssistantSmokeTest

# List available Gemini models for your API key (disabled by default — remove @Disabled first)
mvn test -Dtest=GeminiModelListTest
```

## Environment Variables

The app reads secrets from the environment — never from `application.properties`:

| Variable | Purpose                                              |
|---|------------------------------------------------------|
| `GITHUB_TOKEN` | GitHub API auth (fetch diffs, post comments)         |
| `GITHUB_WEBHOOK_SECRET` | HMAC-SHA256 webhook signature verification           |
| `GOOGLE_GEMINI_API_KEY` | Google AI Studio API key for Gemini LLM from aistudio.google.com        |
| `GOOGLE_GEMINI_MODEL` | Gemini model name (default: `gemini-2.0-flash`)      |
| `SPRING_DATASOURCE_URL` | e.g. `jdbc:postgresql://localhost:5432/codereviewer` |
| `SPRING_DATASOURCE_USERNAME` | DB user                                              |
| `SPRING_DATASOURCE_PASSWORD` | DB password                                          |

For local dev, use `application-local.yml` (git-ignored) with `spring.profiles.active=local`.

## Architecture

This is a Spring Boot 3.x webhook receiver that reviews GitHub PRs automatically using an LLM.

**Request flow:**
1. GitHub sends `POST /webhook/github` on PR open/synchronize
2. `WebhookSignatureFilter` verifies the `X-Hub-Signature-256` HMAC header — returns 401 on mismatch
3. `WebhookController` immediately returns 202 and hands off to `@Async` processing
4. `PullRequestService` orchestrates: fetch diff → chunk → LLM review → publish comments → persist
5. `GitHubApiService` (Spring `RestClient`) fetches PR files and posts the review via GitHub's PR Review API
6. `DiffChunkerService` parses unified diffs, tracks new-file line numbers, and splits files >150 lines into chunks
7. `LLMReviewService` calls the `CodeReviewAssistant` `@AiService` interface (LangChain4j) per chunk
8. `ReviewPublisherService` maps `ReviewComment` results to GitHub's inline comment payload format
9. `ReviewRepository` persists the review and comments to PostgreSQL

**Key design constraints:**
- Webhook must respond in <1s — all LLM/GitHub API work runs `@Async`
- LangChain4j `@AiService` returns typed `ReviewOutput` POJOs directly (no manual JSON parsing)
- Line numbers sent to GitHub must be new-file line numbers from the unified diff, not raw diff positions
- LLM is called per chunk (≤150 lines), not per PR, to stay within token limits
- Only `COMMENT` review events are posted — never `APPROVE` or `REQUEST_CHANGES` (would block human reviewers)

**Package layout** (`com.codereviewer`):
- `controller/` — `WebhookController` (POST /webhook/github), `ReviewController` (GET /api/reviews/**)
- `service/` — `PullRequestService`, `GitHubApiService`, `DiffChunkerService`, `LLMReviewService`, `ReviewPublisherService`
- `ai/` — `CodeReviewAssistant` (@AiService interface), `ReviewPrompts` (system + user prompt constants)
- `model/` — webhook payload POJOs (`PullRequestEvent`, `PullRequestFile`) and LLM output POJOs (`ReviewOutput`, `ReviewComment`)
- `entity/` — JPA entities (`ReviewEntity`, `ReviewCommentEntity`)
- `repository/` — `ReviewRepository extends JpaRepository`
- `dto/` — `ReviewResponse` (API response, never expose JPA entities directly)
- `security/` — `WebhookSignatureFilter` (Servlet filter, `ContentCachingRequestWrapper`)
- `config/` — `AppConfig` (async executor, RestClient bean), `GitHubConfig` (@Value env vars), `LangChain4jConfig` (ChatModel bean)

## LLM Integration

- Provider: Google Gemini (direct via Google AI Studio key)
- Default model: `gemini-2.0-flash` (override with `GOOGLE_GEMINI_MODEL` env var — optional)
- LangChain4j version: `0.36.0` (managed via BOM)
- Dependency: `langchain4j-google-ai-gemini` (core module; no Spring Boot starter needed — bean is defined manually in `LangChain4jConfig`)
- The `CodeReviewAssistant` interface uses `@AiService` and returns `String` — prompt is passed via `@UserMessage`
- Retry: `.maxRetries(0)` — auto-retry is disabled to avoid burning free-tier quota; retry logic belongs at the service layer

### Free-tier quota notes

The Gemini free tier has a daily request cap per model. If you hit a 429:
1. Run `GeminiModelListTest` to see all models your key can access
2. Set `GOOGLE_GEMINI_MODEL` in `application-local.yml` to a model with remaining quota (e.g. `gemini-2.0-flash-lite`)
3. Quota resets at midnight Pacific time

```bash
# List available models for your API key
mvn test -Dtest=GeminiModelListTest

# Run the LLM smoke test (requires PostgreSQL + API key in application-local.yml)
mvn test -Dtest=CodeReviewAssistantSmokeTest
```

## Branch & PR Convention

One branch per day, one PR per day merging to `main`:

```
feat/day1-project-setup       → PR #1
feat/day2-webhook-security    → PR #2
feat/day3-github-api-client   → PR #3
...
```

PR titles follow: `Day N — <goal from PLAN.md>`. See `PLAN.md` for the full 14-day execution plan.
