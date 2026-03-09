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

ConnectWise 🧩:
- `company.id`
- `company.idnumber`
- `public.key`
- `private.key`
- `client.id`

Slack 💬:
- `slack.bot.token`
- `slack.channel.id` (default fallback channel)

AWS / DynamoDB ☁️:
- `aws.region`
- `aws.dynamodb.table`
- `aws.accessKeyId`
- `aws.secretAccessKey`

Assignment defaults 👤:
- `user.id`
- `user.identifier`
- `lead.contact.name`

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
- `POST /api/connectwise/events`
- `POST /api/slack/events`
