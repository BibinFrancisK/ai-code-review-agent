# Local Development Guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 17+ | `java -version` to confirm |
| Maven | 3.9+ | `mvn -version` to confirm |
| Docker Desktop | Latest | Required for Testcontainers + local PostgreSQL |
| ngrok | Latest | For routing GitHub webhooks to localhost |

---

## Environment Variables

Create `src/main/resources/application-local.yml` (git-ignored) with your secrets:

```yaml
github:
  token: <your-github-pat>
  webhook:
    secret: <any-random-string — e.g. openssl rand -hex 20>

google:
  gemini:
    api-key: <your-google-ai-studio-key>
    model: gemini-2.0-flash        # override if quota is exhausted

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/codereviewer
    username: postgres
    password: postgres
```

Get a free Gemini API key at [aistudio.google.com](https://aistudio.google.com).

---

## Running Locally

```bash
# 1. Start PostgreSQL (Flyway runs migrations on boot)
docker run -d --name pg-local \
  -e POSTGRES_DB=codereviewer \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:16-alpine

# 2. Run the app on the local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. Verify health
curl localhost:8080/actuator/health
# Expected: {"status":"UP"}

# 4. Expose to the internet for real GitHub webhooks
ngrok http 8080
# Copy the https:// URL → paste as GitHub webhook Payload URL
# Repo Settings → Webhooks → edit → set URL, select "Pull requests" event
```

> **ngrok tip:** Always use the `https://` URL ngrok gives you (not `http://`). Do not append a port number — ngrok handles the port mapping internally.

---

## Running Tests

```bash
# Unit + integration tests (Testcontainers starts PostgreSQL automatically)
mvn test

# Full verify with JaCoCo coverage gate (≥70% line coverage required)
mvn verify

# Run a single test class
mvn test -Dtest=RateLimitingFilterTest

# Run a single test method
mvn test -Dtest=WebhookControllerTest#methodName

# LLM smoke test — requires application-local.yml with a real API key
# Remove @Disabled from CodeReviewAssistantSmokeTest first
mvn test -Dtest=CodeReviewAssistantSmokeTest
```

---

## Gemini Quota Management

The free tier has a daily request cap per model. If you receive HTTP 429:

```bash
# List all models available for your API key
mvn test -Dtest=GeminiModelListTest
```

Then switch to a model with remaining quota in `application-local.yml`:

```yaml
google:
  gemini:
    model: gemini-2.0-flash-lite   # lighter quota usage
```

Quota resets at midnight Pacific time.

---

## Flyway Migration Tips

- Migrations live in `src/main/resources/db/migration/`
- Naming convention: `V{n}__{description}.sql` (two underscores)
- To reset the local DB: `docker rm -f pg-local` and re-run Step 1 above
- Never edit an already-applied migration — add a new versioned file instead
- Flyway runs automatically on startup; look for `Successfully applied N migration(s)` in the logs
