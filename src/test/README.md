# Test Suite — Setup & Running Guide

## Unit tests

Run without any setup:

```bash
mvn test
```

## Integration & smoke tests (`@Disabled` by default)

These tests hit live external services (GitHub API, Gemini API, PostgreSQL) and are disabled in CI.
To run one manually:

1. **Set environment variables**

   | Variable | Purpose |
   |---|---|
   | `GITHUB_TOKEN` | GitHub API auth — fetch PR diffs |
   | `GOOGLE_GEMINI_API_KEY` | Google AI Studio key for Gemini LLM |

2. **Start PostgreSQL**

   ```bash
   docker compose up -d
   ```

3. **Create `src/main/resources/application-local.yml`** (git-ignored) with your values:

   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/codereviewer
       username: your_user
       password: your_password
   ```

4. **Remove `@Disabled`** from the target test class, run it, then **re-add `@Disabled`** before committing.

   ```bash
   # Run a specific test class
   mvn test -Dtest=CodeReviewAssistantSmokeTest
   mvn test -Dtest=GeminiModelListTest
   mvn test -Dtest=PullRequestServiceIntegrationTest
   ```

## Tests requiring live dependencies

| Test class | What it tests | Dependencies |
|---|---|---|
| `CodeReviewAssistantSmokeTest` | LLM returns structured `ReviewOutput` for real diffs | Gemini API, PostgreSQL |
| `GeminiModelListTest` | Lists available Gemini models for your API key | Gemini API, PostgreSQL |
| `PullRequestServiceIntegrationTest` | Full pipeline: fetch PR diff → chunk → LLM review | GitHub API, Gemini API, PostgreSQL |
