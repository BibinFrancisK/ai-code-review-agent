# AWS CDK Deployment

Deploys the AI Code Review Agent to an AWS EC2 t2.micro instance (free tier eligible). Infrastructure is defined in TypeScript CDK and deployed automatically on every push to `main` via GitHub Actions.

## Prerequisites

These steps are one-time manual setup. Complete them before pushing to `main`.

### 1. Create IAM User for GitHub Actions

- AWS Console → IAM → Users → Create user: `github-actions-deployer`
- Attach the following policies:
  - `AmazonEC2FullAccess`
  - `AmazonEC2ContainerRegistryFullAccess`
  - `AmazonS3FullAccess`
  - `IAMFullAccess`
  - `AWSCloudFormationFullAccess`
  - `AmazonSSMFullAccess`
- Create an access key for the user and save the credentials for step 4.

### 2. Store App Secrets in SSM Parameter Store

AWS Console → Systems Manager → Parameter Store → Create parameter.

Create each of the following as type **SecureString**:

| Parameter name | Value |
|---|---|
| `/ai-code-reviewer/GITHUB_TOKEN` | Your GitHub personal access token |
| `/ai-code-reviewer/GITHUB_WEBHOOK_SECRET` | Your webhook secret |
| `/ai-code-reviewer/GOOGLE_GEMINI_API_KEY` | Your Google AI Studio API key |
| `/ai-code-reviewer/GOOGLE_GEMINI_MODEL` | e.g. `gemini-2.0-flash` |
| `/ai-code-reviewer/SPRING_DATASOURCE_URL` | e.g. `jdbc:postgresql://db:5432/codereviewer` |
| `/ai-code-reviewer/SPRING_DATASOURCE_USERNAME` | e.g. `postgres` |
| `/ai-code-reviewer/SPRING_DATASOURCE_PASSWORD` | PostgreSQL password |

The EC2 instance reads these at boot time via `aws ssm get-parameter --with-decryption`. No secrets are stored in code or GitHub Actions environment variables.

### 3. Create an EC2 Key Pair

- AWS Console → EC2 → Key Pairs → Create key pair
- Name: `ai-code-reviewer-key`
- Download the `.pem` file and run `chmod 400 ai-code-reviewer-key.pem`

This key pair is used for SSH access to the instance if you need to debug.

### 4. Add GitHub Actions Secrets

Repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret name | Value |
|---|---|
| `AWS_ACCESS_KEY_ID` | Access key ID from step 1 |
| `AWS_SECRET_ACCESS_KEY` | Secret access key from step 1 |
| `AWS_REGION` | e.g. `us-east-1` |
| `DOCKERHUB_USERNAME` | Your Docker Hub username |

## How Deployment Works

Every push to `main` triggers `.github/workflows/cdk-deploy.yml`, which:

1. Configures AWS credentials from GitHub secrets
2. Runs `cdk bootstrap` (idempotent — safe to run on every push)
3. Runs `cdk deploy --require-approval never`

CloudFormation diffs the stack and replaces the EC2 instance if anything changed. The instance boots, pulls the latest Docker image from Docker Hub, reads secrets from SSM, and starts the PostgreSQL and app containers.

> Note: EC2 instance replacement takes ~2–3 min. 

## After Deployment

The CDK deploy step logs two outputs:

- `PublicIp` — EC2 instance public IP address
- `WebhookUrl` — full webhook URL to paste into GitHub (`http://<ip>:8080/webhook/github`)

Update your GitHub webhook: repo → Settings → Webhooks → edit → set Payload URL to the `WebhookUrl` output.

Health check:

```bash
curl http://<ec2-public-ip>:8080/actuator/health
# Expected: {"status":"UP"}
```
