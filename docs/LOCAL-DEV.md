# Local Development

**Prerequisites:** Docker, Java 17, Maven

```bash
# 1. Clone and configure secrets
git clone https://github.com/BibinFrancisK/ai-code-review-agent.git
cd ai-code-review-agent
cp docs/.env.example .env
# Fill in: GITHUB_TOKEN, GITHUB_WEBHOOK_SECRET, GOOGLE_GEMINI_API_KEY

# 2. Start the full stack (app + PostgreSQL)
docker compose up -d

# 3. Confirm healthy
curl localhost:8080/actuator/health

# 4. Expose locally for webhook testing
ngrok http 8080
# → copy the https:// URL and set it as the GitHub webhook Payload URL
# → Repo Settings → Webhooks → edit → set URL, select "Pull requests" event
```

Get a free Gemini API key at [aistudio.google.com](https://aistudio.google.com).
