export function buildUserDataCommands(region: string, dockerhubUsername: string): string[] {
  const ssm = (name: string) =>
    `$(aws ssm get-parameter --name /ai-code-reviewer/${name} --with-decryption --query Parameter.Value --output text --region ${region})`;

  return [
    'apt-get update -y',
    'apt-get install -y docker.io awscli',
    'systemctl enable --now docker',

    // Wait until SSM is accessible (tests the full auth + KMS decrypt path)
    `until aws ssm get-parameter --name /ai-code-reviewer/GITHUB_TOKEN --with-decryption --region ${region} --query Parameter.Value --output text > /dev/null 2>&1; do echo "Waiting for SSM access..."; sleep 5; done`,

    `GITHUB_TOKEN=${ssm('GITHUB_TOKEN')}`,
    `WEBHOOK_SECRET=${ssm('GITHUB_WEBHOOK_SECRET')}`,
    `GEMINI_KEY=${ssm('GOOGLE_GEMINI_API_KEY')}`,
    `GEMINI_MODEL=${ssm('GOOGLE_GEMINI_MODEL')}`,
    `DB_URL=${ssm('SPRING_DATASOURCE_URL')}`,
    `DB_USERNAME=${ssm('SPRING_DATASOURCE_USERNAME')}`,
    `DB_PASSWORD=${ssm('SPRING_DATASOURCE_PASSWORD')}`,

    'docker run -d --name db -p 5432:5432' +
      ' -e POSTGRES_DB=codereviewer -e POSTGRES_USER=$DB_USERNAME -e POSTGRES_PASSWORD=$DB_PASSWORD' +
      ' --restart unless-stopped postgres:16-alpine',

    `docker run -d --name app --link db:db -p 8080:8080` +
      ` -e GITHUB_TOKEN=$GITHUB_TOKEN` +
      ` -e GITHUB_WEBHOOK_SECRET=$WEBHOOK_SECRET` +
      ` -e GOOGLE_GEMINI_API_KEY=$GEMINI_KEY` +
      ` -e GOOGLE_GEMINI_MODEL=$GEMINI_MODEL` +
      ` -e SPRING_DATASOURCE_URL=$DB_URL` +
      ` -e SPRING_DATASOURCE_USERNAME=$DB_USERNAME` +
      ` -e SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD` +
      ` --restart unless-stopped` +
      ` ${dockerhubUsername}/ai-code-reviewer:latest`,
  ];
}
