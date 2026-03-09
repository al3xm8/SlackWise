# Security Cleanup Today

This checklist is for immediate credential hygiene and rotation.

## 1. Rotate all exposed credentials now

Rotate these in this order:

1. `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`
2. `SLACK_BOT_TOKEN`
3. `CONNECTWISE_PUBLIC_KEY` / `CONNECTWISE_PRIVATE_KEY` / `CONNECTWISE_CLIENT_ID`

After each rotation, verify old credentials are invalidated.

## 2. Keep secrets out of git

- `src/main/resources/application.properties` now reads from environment variables only.
- Do not put raw secrets in source files, commits, PR comments, or screenshots.

## 3. Set local environment variables (PowerShell)

Use a local shell profile or run these per session:

```powershell
$env:SLACK_BOT_TOKEN="xoxb-REDACTED"
$env:SLACK_CHANNEL_ID="C01234567"
$env:SLACK_CLIENT_ID="REDACTED"
$env:SLACK_CLIENT_SECRET="REDACTED"
$env:SLACK_OAUTH_REDIRECT_URI="https://your-api-domain/api/slack/oauth/callback"

$env:COMPANY_ID="your-connectwise-company-identifier"
$env:COMPANY_ID_NUMBER="19300"
$env:CONNECTWISE_PUBLIC_KEY="REDACTED"
$env:CONNECTWISE_PRIVATE_KEY="REDACTED"
$env:CONNECTWISE_CLIENT_ID="REDACTED"

$env:AWS_REGION="us-east-2"
$env:AWS_DYNAMODB_TABLE="slackwise-alltable-clients"

# Optional (prefer IAM role/default provider chain in production)
$env:AWS_ACCESS_KEY_ID="REDACTED"
$env:AWS_SECRET_ACCESS_KEY="REDACTED"

$env:LEAD_CONTACT_NAME="Tech Lead Name"
$env:DEFAULT_USER_ID="200"
$env:DEFAULT_USER_IDENTIFIER="amatos"
```

Then run:

```powershell
.\mvnw spring-boot:run
```

## 4. Store production secrets in AWS Secrets Manager

Create one secret per environment (example key/value JSON):

```json
{
  "SLACK_BOT_TOKEN": "xoxb-...",
  "SLACK_CHANNEL_ID": "C...",
  "SLACK_CLIENT_ID": "...",
  "SLACK_CLIENT_SECRET": "...",
  "SLACK_OAUTH_REDIRECT_URI": "https://your-api-domain/api/slack/oauth/callback",
  "COMPANY_ID": "tenant",
  "COMPANY_ID_NUMBER": "19300",
  "CONNECTWISE_PUBLIC_KEY": "...",
  "CONNECTWISE_PRIVATE_KEY": "...",
  "CONNECTWISE_CLIENT_ID": "...",
  "AWS_REGION": "us-east-2",
  "AWS_DYNAMODB_TABLE": "slackwise-alltable-clients",
  "LEAD_CONTACT_NAME": "...",
  "DEFAULT_USER_ID": "200",
  "DEFAULT_USER_IDENTIFIER": "..."
}
```

Inject these into your runtime as environment variables (ECS task definition, EC2 systemd env, or CI/CD deploy step).

## 5. Purge historical leaks from git history

If secrets were ever committed, rotate first, then rewrite history.

Recommended tool:
- `git filter-repo` to remove sensitive blobs from all branches/tags.

After rewrite:
1. Force-push rewritten refs.
2. Invalidate old clones.
3. Ask collaborators to fresh-clone.

## 6. Verify and monitor

Run a quick grep before shipping:

```powershell
rg -n "xoxb-|AKIA|aws.secretAccessKey|private.key=|public.key=|client.id=|auth.token=" -S .
```

Add automated secret scanning in CI (for example: Gitleaks or GitHub secret scanning).
