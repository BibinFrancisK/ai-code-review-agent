# AI Code Review Agent

> Automatically reviews GitHub PRs using LLM analysis — posts inline comments for bugs, security vulnerabilities, and performance anti-patterns.

[![CI](https://github.com/BibinFrancisK/ai-code-review-agent/actions/workflows/ci.yml/badge.svg)](https://github.com/BibinFrancisK/ai-code-review-agent/actions/workflows/ci.yml)
[![Docker](https://img.shields.io/docker/pulls/bibinfrancisk/ai-code-reviewer)](https://hub.docker.com/r/bibinfrancisk/ai-code-reviewer)
![Status](https://img.shields.io/badge/status-in%20progress-yellow)

---

## Demo

> Screenshot / GIF coming in Day 14 — see `docs/screenshots/` folder

---

## Architecture

```
    GitHub PR Event (open / synchronize)
        │
        ▼ POST /webhook/github
┌─────────────────────────────────────────────────────────┐
│                                         Spring Boot App │
│  WebhookController                                      │
│       │ (validates HMAC-SHA256, responds 202 in <1s)    │
│       ▼                                                 │
│  PullRequestService  ──► GitHubApiService               │
│       │ (fetches PR diff/ files)                        │
│       ▼                                                 │
│  DiffChunkerService                                     │
│       │ (splits files into ≤150-line chunks)            │
│       ▼                                                 │
│  LLMReviewService  ──► LLM API                          │
│       │ (generates review)                              │
│       ▼                                                 │
│  ReviewPublisherService ──► GitHub Review API           │
│       │ (posts inline PR comments)                      │
│       ▼                                                 │
│  ReviewRepository ──► PostgreSQL                        │
│         (persists review history)                       │
└─────────────────────────────────────────────────────────┘
        │
        ▼  GET /api/reviews  
          (retrieve review history)
```

---

## What It Detects

| Category    | Examples                                                |
|-------------|---------------------------------------------------------|
| Security    | SQL injection, hardcoded secrets, OWASP Top 10 etc.     |
| Bugs        | Null pointer risks, race conditions, off-by-one etc.    |
| Performance | N+1 queries, O(n²) loops, unnecessary allocations etc.  |
| Quality     | Swallowed exceptions, SOLID violations, dead code  etc. |

---

## How It Works

1. GitHub sends a webhook on PR open or push
2. `WebhookSignatureFilter` verifies the `X-Hub-Signature-256` HMAC header — returns 401 on mismatch
3. `PullRequestService` hands off to an `@Async` thread — webhook returns 202 in < 1s
4. PR diff is fetched via GitHub API and split into ≤ 150-line chunks
5. Each chunk is reviewed by Google Gemini and returned as structured JSON
6. Inline review comments are posted directly on the PR via the GitHub Review API
7. Review and all comments are persisted to PostgreSQL

---

## Tech Stack

| Layer       | Technology                                      |
|-------------|-------------------------------------------------|
| Framework   | Java 17, Spring Boot 3.x                        |
| LLM         | Google Gemini (via LangChain4j)                 |
| Database    | PostgreSQL + Flyway                             |
| Deployment  | AWS EC2 t2.micro (CDK + GitHub Actions)         |
| Auth        | GitHub OAuth token introspection                |
| API Docs    | Springdoc OpenAPI / Swagger UI                  |
| Metrics     | Micrometer (via Spring Boot Actuator)           |

---

## Performance

| Metric               | Value                                              |
|----------------------|----------------------------------------------------|
| Average review time  | _TBD — measure across 5+ PRs, target < 30s_       |
| Max PR size tested   | _TBD lines_                                        |
| Issue distribution   | _TBD % bugs / % security / % quality_             |
| False positive rate  | _TBD — % of comments marked unhelpful_            |

---

## API

The review history API is protected by GitHub OAuth token introspection. Pass any valid GitHub PAT as a Bearer token.

```bash
# Set your host
# HOST=http://localhost:8080       
# HOST=http://<ec2-ip>:8080 

# List all reviews (paginated)
curl -H "Authorization: Bearer <your-github-pat>" $HOST/api/reviews

# Get a specific review with all comments
curl -H "Authorization: Bearer <your-github-pat>" $HOST/api/reviews/{id}

# All reviews for a repo
curl -H "Authorization: Bearer <your-github-pat>" $HOST/api/reviews/repo/{owner}/{repo}

# Reviews for a specific PR
curl -H "Authorization: Bearer <your-github-pat>" $HOST/api/reviews/pr/{owner}/{repo}/{prNumber}
```

Full interactive docs at `$HOST/swagger-ui.html`.

---

## Local Setup

See [docs/README-local-dev.md](docs/README-local-dev.md) for the full local development guide.

---

## Deployment

The app is deployed to AWS EC2 via TypeScript CDK and GitHub Actions. See [cdk/README.md](cdk/README.md) for one-time setup prerequisites and how the deployment pipeline works.

Every push to `main` triggers `cdk deploy` — CloudFormation diffs and updates the EC2 instance automatically.

---

## Screenshots

> Full screenshots will be added in Day 14.

- Inline bot comment on a GitHub PR (SQL injection catch)
- Swagger UI at `/swagger-ui.html`
- Review history via `GET /api/reviews`
