#!/bin/bash
set -euo pipefail

REGION="__REGION__"
DOCKERHUB_USERNAME="__DOCKERHUB_USERNAME__"

ssm() {
  aws ssm get-parameter --name "/ai-code-reviewer/$1" --with-decryption \
    --query Parameter.Value --output text --region "$REGION"
}

apt-get update -y
apt-get install -y docker.io awscli
systemctl enable --now docker

snap install amazon-ssm-agent --classic
systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service

# Write redeploy.sh immediately after SSM agent is up so it is on disk
# before the agent registers — the workflow polls for Online before sending.
cat > /usr/local/bin/redeploy.sh << 'REDEPLOY'
#!/bin/bash
set -euo pipefail

REGION="__REGION__"
DOCKERHUB_USERNAME="__DOCKERHUB_USERNAME__"

ssm() {
  aws ssm get-parameter --name "/ai-code-reviewer/$1" --with-decryption \
    --query Parameter.Value --output text --region "$REGION"
}

docker pull "${DOCKERHUB_USERNAME}/ai-code-reviewer:latest"
docker stop app 2>/dev/null || true
docker rm   app 2>/dev/null || true

GITHUB_TOKEN=$(ssm GITHUB_TOKEN)
WEBHOOK_SECRET=$(ssm GITHUB_WEBHOOK_SECRET)
GEMINI_KEY=$(ssm GOOGLE_GEMINI_API_KEY)
GEMINI_MODEL=$(ssm GOOGLE_GEMINI_MODEL)
DB_URL=$(ssm SPRING_DATASOURCE_URL)
DB_USERNAME=$(ssm SPRING_DATASOURCE_USERNAME)
DB_PASSWORD=$(ssm SPRING_DATASOURCE_PASSWORD)

docker run -d --name app --link db:db -p 8080:8080 \
  -e GITHUB_TOKEN="$GITHUB_TOKEN" \
  -e GITHUB_WEBHOOK_SECRET="$WEBHOOK_SECRET" \
  -e GOOGLE_GEMINI_API_KEY="$GEMINI_KEY" \
  -e GOOGLE_GEMINI_MODEL="$GEMINI_MODEL" \
  -e SPRING_DATASOURCE_URL="$DB_URL" \
  -e SPRING_DATASOURCE_USERNAME="$DB_USERNAME" \
  -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  --log-driver=awslogs \
  --log-opt awslogs-region="$REGION" \
  --log-opt awslogs-group=/ai-code-reviewer/app \
  --log-opt awslogs-create-group=true \
  --restart unless-stopped \
  "${DOCKERHUB_USERNAME}/ai-code-reviewer:latest"
REDEPLOY

sed -i "s/__REGION__/$REGION/g; s/__DOCKERHUB_USERNAME__/$DOCKERHUB_USERNAME/g" \
  /usr/local/bin/redeploy.sh
chmod +x /usr/local/bin/redeploy.sh

until ssm GITHUB_TOKEN > /dev/null 2>&1; do
  echo "Waiting for SSM access..."
  sleep 5
done

GITHUB_TOKEN=$(ssm GITHUB_TOKEN)
WEBHOOK_SECRET=$(ssm GITHUB_WEBHOOK_SECRET)
GEMINI_KEY=$(ssm GOOGLE_GEMINI_API_KEY)
GEMINI_MODEL=$(ssm GOOGLE_GEMINI_MODEL)
DB_URL=$(ssm SPRING_DATASOURCE_URL)
DB_USERNAME=$(ssm SPRING_DATASOURCE_USERNAME)
DB_PASSWORD=$(ssm SPRING_DATASOURCE_PASSWORD)

docker run -d --name db -p 5432:5432 \
  -e POSTGRES_DB=codereviewer \
  -e POSTGRES_USER="$DB_USERNAME" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  --log-driver=awslogs \
  --log-opt awslogs-region="$REGION" \
  --log-opt awslogs-group=/ai-code-reviewer/db \
  --log-opt awslogs-create-group=true \
  --restart unless-stopped \
  postgres:16-alpine

docker run -d --name app --link db:db -p 8080:8080 \
  -e GITHUB_TOKEN="$GITHUB_TOKEN" \
  -e GITHUB_WEBHOOK_SECRET="$WEBHOOK_SECRET" \
  -e GOOGLE_GEMINI_API_KEY="$GEMINI_KEY" \
  -e GOOGLE_GEMINI_MODEL="$GEMINI_MODEL" \
  -e SPRING_DATASOURCE_URL="$DB_URL" \
  -e SPRING_DATASOURCE_USERNAME="$DB_USERNAME" \
  -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  --log-driver=awslogs \
  --log-opt awslogs-region="$REGION" \
  --log-opt awslogs-group=/ai-code-reviewer/app \
  --log-opt awslogs-create-group=true \
  --restart unless-stopped \
  "${DOCKERHUB_USERNAME}/ai-code-reviewer:latest"
