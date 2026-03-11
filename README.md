# SlackWise ⚡

SlackWise syncs ConnectWise service tickets, notes, and status changes into Slack threads so teams can work from Slack without losing ConnectWise context.

## Current capabilities ✅
- Ingest ConnectWise ticket events and post/update Slack threads.
- Map Slack thread replies back into ConnectWise notes/time entries.
- Store per-tenant config, routing rules, and ticket/thread mappings in DynamoDB.
- Route tickets by rule conditions (`SUBJECT`, `CONTACT`, `COMPANY_ID`) to specific Slack channels and assignees.
- Dashboard and Catch Up frontend pages for ticket stats and ticket actions.
- Settings page for tenant config, automation controls, and light/dark theme.

## Repository layout 🗂️
- `src/main/java/...` Spring Boot backend (controllers, services, models).
- `src/main/resources/application.properties` backend runtime config.
- `frontend/` React + TypeScript + Vite application.

## Local development 🛠️

### Backend ☕
1. Build:
```powershell
.\mvnw package -DskipTests
```
2. Run:
```powershell
.\mvnw spring-boot:run
```

### Frontend 🎨
1. Install dependencies:
```powershell
npm --prefix frontend install
```
2. Run dev server:
```powershell
npm --prefix frontend run dev
```
3. Production build:
```powershell
npm --prefix frontend run build
```

Notes 📌:
- The frontend calls `/api/...` paths. In local dev, use a same-origin setup (proxy/reverse proxy) or equivalent routing so `/api` resolves to the Spring app.
- To receive webhook callbacks locally, expose backend port `8080` (for example with `ngrok http 8080`).

## Required configuration 🔧
Set runtime configuration via environment variables (do not commit secrets in `application.properties`).
Reference template: `src/main/resources/application.properties.example`.

ConnectWise 🧩:
- `COMPANY_ID`
- `COMPANY_ID_NUMBER`
- `CONNECTWISE_PUBLIC_KEY`
- `CONNECTWISE_PRIVATE_KEY`
- `CONNECTWISE_CLIENT_ID`

Slack 💬:
- `SLACK_BOT_TOKEN`
- `SLACK_CHANNEL_ID` (default fallback channel)
- `SLACK_CLIENT_ID` (for OAuth install flow)
- `SLACK_CLIENT_SECRET` (for OAuth callback token exchange)
- `SLACK_OAUTH_REDIRECT_URI` (must exactly match Slack app Redirect URL)
- `SLACK_OAUTH_BOT_SCOPES` (optional override)
- `SLACK_OAUTH_USER_SCOPES` (optional override)

AWS / DynamoDB ☁️:
- `AWS_REGION`
- `AWS_DYNAMODB_TABLE`
- `AWS_ACCESS_KEY_ID` (optional; prefer IAM role/default credential chain)
- `AWS_SECRET_ACCESS_KEY` (optional; prefer IAM role/default credential chain)

Assignment defaults 👤:
- `DEFAULT_USER_ID`
- `DEFAULT_USER_IDENTIFIER`
- `LEAD_CONTACT_NAME`

Security cleanup today:
- Rotate any previously committed Slack, ConnectWise, and AWS credentials.
- Remove old credentials from source history.
- Store production secrets in AWS Secrets Manager and inject as environment variables at deploy time.
- Detailed runbook: `docs/SECURITY_CLEANUP_TODAY.md`

## Tenant configuration fields 🏢
Stored at `tenantId + sk=CONFIG`:
- `displayName`
- `slackTeamId`
- `slackBotToken`
- `defaultChannelId`
- `connectwiseSite`
- `connectwiseClientId`
- `connectwisePublicKey`
- `connectwisePrivateKey`
- `autoAssignmentDelayMinutes`
- `assignmentExclusionKeywords` (comma/semicolon/newline delimited)
- `trackedCompanyIds` (comma/semicolon/newline delimited)
- `themeMode` (`light` or `dark`)

## DynamoDB single-table design 🧱
Keys 🔑:
- Partition key: `tenantId` (String)
- Sort key: `sk` (String)

Item types 📦:
- Tenant config: `sk = CONFIG`
- Routing rule: `sk = RULE#0001#<ruleId>` (priority padded)
- Ticket/thread mapping: `sk = TICKET#<ticketId>`

## API summary 🧩

Tenant admin 👨‍💼:
- `GET /api/tenants/default`
- `GET /api/tenants/{tenantId}`
- `PUT /api/tenants/{tenantId}`
- `GET /api/tenants/{tenantId}/rules`
- `POST /api/tenants/{tenantId}/rules`
- `PUT /api/tenants/{tenantId}/rules/{ruleId}`
- `DELETE /api/tenants/{tenantId}/rules/{ruleId}?priority=NN`

Tickets 🎫:
- `GET /api/tickets/stats`
- `GET /api/tickets/open`
- `POST /api/tickets/{ticketId}/responses`
- `PATCH /api/tickets/{ticketId}/status`

Webhooks/events 🔔:
- `GET /api/slack/oauth/install?tenantId=<tenant>`
- `GET /api/slack/oauth/callback`
- `POST /api/connectwise/events`
- `POST /api/slack/events`

## Auth (OIDC + JWT) 🔐

Backend API auth is controlled with:
- `APP_AUTH_ENABLED` (`false` for local fallback, `true` for production)
- `OAUTH_ISSUER_URI` (JWT issuer URI)
- `OAUTH_AUDIENCE` (API audience)
- `APP_AUTH_TENANT_CLAIMS` (comma-separated claim names checked for tenant id, e.g. `tenant_id,tenantId,company_id,org_id`)

When enabled:
- `/api/tenants/**` and `/api/tickets/**` require a bearer token.
- Tenant access is enforced from JWT claims.

Frontend OIDC config (`frontend/.env.example`):
- `VITE_OIDC_ISSUER`
- `VITE_OIDC_CLIENT_ID`
- `VITE_OIDC_AUDIENCE`
- `VITE_OIDC_SCOPE`
- `VITE_OIDC_REDIRECT_PATH` (default `/auth/callback`)
- `VITE_OIDC_TENANT_CLAIMS`

UI auth flow:
- Sign in/up page can start hosted OIDC login.
- Callback route is `/auth/callback`.
- Access token is attached automatically to API requests from the frontend session.
