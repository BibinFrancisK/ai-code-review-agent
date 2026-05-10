# Deploy Pipeline Improvements

## Context

Two gaps in the current CI/CD pipeline:

1. **EC2 never picks up new images.** `cdk-deploy.yml` runs `cdk deploy` after every Docker push, but CloudFormation only replaces the EC2 instance when the CDK stack *definition* changes. Since no stack definition changes on a code push, CloudFormation is a no-op and the existing instance keeps running the old container indefinitely.

2. **Only `:latest` tag exists on DockerHub.** There is no rollback point and no way to audit which commit is running in production. `docker-publish.yml` always overwrites the same tag.

---

## Problem 1 — EC2 Auto-Redeploy via SSM Run Command

**Approach**: after CDK deploy, the workflow sends an SSM Run Command to the already-running instance to pull the new image and restart the app container — no instance replacement, no downtime.

### `cdk/scripts/ec2-user-data.sh`

Two additions at boot time:

1. Install and enable the SSM agent (required for SSM Run Command to reach the instance):
   ```bash
   snap install amazon-ssm-agent --classic
   systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service
   ```

2. Write `/usr/local/bin/redeploy.sh` to disk immediately after the initial `docker run` block. The script:
   - `docker pull ${DOCKERHUB_USERNAME}/ai-code-reviewer:latest`
   - `docker stop app && docker rm app` (with `|| true` so it's safe on first run too)
   - Re-fetches SSM params and runs the same `docker run` command
   - Make it executable (`chmod +x`)

   The placeholders `__REGION__` and `__DOCKERHUB_USERNAME__` are already substituted by CDK at deploy time, so they can be embedded in the written script.

### `cdk/lib/app-stack.ts`

Add the `AmazonSSMManagedInstanceCore` managed policy to the EC2 instance IAM role (`AppRole`). This allows the SSM agent on the instance to register and receive commands:
```ts
iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore'),
```

> The GitHub Actions IAM user already has `AmazonSSMFullAccess` (per `docs/CDK.md`) which covers `ssm:SendCommand`. No change needed there.

### `.github/workflows/cdk-deploy.yml`

Add two steps after `CDK Deploy`:

**Step: Get EC2 instance ID**
```bash
INSTANCE_ID=$(aws cloudformation describe-stack-resource \
  --stack-name AiCodeReviewerStack \
  --logical-resource-id AppInstance \
  --query StackResourceDetail.PhysicalResourceId \
  --output text)
echo "INSTANCE_ID=$INSTANCE_ID" >> $GITHUB_ENV
```

**Step: Send redeploy command and wait**
```bash
COMMAND_ID=$(aws ssm send-command \
  --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=["/usr/local/bin/redeploy.sh"]' \
  --comment "Redeploy after image push" \
  --query Command.CommandId --output text)

aws ssm wait command-executed \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE_ID"
```

> `wait command-executed` polls until success/failure (timeout ~1000s). Spring Boot starts in ~15–20s on t3.micro, so this is well within range.

---

## Problem 2 — Docker Image Tagging + DockerHub Retention (last 4)

**Approach**: push two tags per build (`:latest` + `:sha-<7-char-git-sha>`), then call the DockerHub API to delete SHA tags beyond the 4 most recent.

### `.github/workflows/docker-publish.yml`

**Change 1 — dual tags on the build step:**
```yaml
tags: |
  ${{ secrets.DOCKERHUB_USERNAME }}/ai-code-reviewer:latest
  ${{ secrets.DOCKERHUB_USERNAME }}/ai-code-reviewer:sha-${{ github.sha[:7] }}
```

**Change 2 — add a cleanup step after the push:**
```bash
# Get DockerHub JWT
TOKEN=$(curl -sf -X POST https://hub.docker.com/v2/users/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${{ secrets.DOCKERHUB_USERNAME }}\",\"password\":\"${{ secrets.DOCKERHUB_TOKEN }}\"}" \
  | jq -r .token)

REPO="${{ secrets.DOCKERHUB_USERNAME }}/ai-code-reviewer"

# List sha-* tags sorted newest-first, delete any beyond position 4
curl -sf "https://hub.docker.com/v2/repositories/${REPO}/tags/?page_size=100" \
  -H "Authorization: Bearer $TOKEN" \
  | jq -r '.results | map(select(.name | startswith("sha-")))
           | sort_by(.last_updated) | reverse | .[4:] | .[].name' \
  | while read -r tag; do
      echo "Deleting old tag: $tag"
      curl -sf -X DELETE \
        "https://hub.docker.com/v2/repositories/${REPO}/tags/${tag}/" \
        -H "Authorization: Bearer $TOKEN"
    done
```

> **Prerequisite**: `DOCKERHUB_TOKEN` must have **Read, Write, Delete** scope. If it was created as read-only, regenerate it in DockerHub → Account Settings → Personal Access Tokens and update the GitHub secret.

---

## Files Changed

| File | Change |
|---|---|
| `cdk/scripts/ec2-user-data.sh` | Install SSM agent; write `/usr/local/bin/redeploy.sh` to disk |
| `cdk/lib/app-stack.ts` | Add `AmazonSSMManagedInstanceCore` to `AppRole` managed policies |
| `.github/workflows/cdk-deploy.yml` | Add instance-ID lookup + SSM send-command + wait steps |
| `.github/workflows/docker-publish.yml` | Add SHA tag; add DockerHub tag cleanup step |

---

## Verification

1. Push any change to `main`.
2. **DockerHub**: repository shows `:latest` + `:sha-<sha>` tags; old SHA tags beyond 4 are deleted.
3. **GitHub Actions — CDK Deploy**: SSM step logs the command ID and waits; status shows `Success`.
4. **AWS Console → Systems Manager → Run Command → Command history**: command shows `Success`.
5. **On EC2**: `docker ps` shows `app` container with a recent start time; `docker images` shows the freshly pulled image.
