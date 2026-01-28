# SlackWise ⚡

SlackWise syncs ConnectWise service tickets and notes into Slack threads so teams can collaborate where they already work. It is built for real integrations and a path to a multi-tenant, client-facing dashboard.

## Why it exists ✅
- Keep ticket context in one place (Slack threads)
- Avoid duplicate posts (DynamoDB-backed mappings)
- Support per-client routing rules (subject/contact/regex -> channel)

## What is in this repo 🧰
- Spring Boot backend that listens for ConnectWise events and posts to Slack
- DynamoDB single-table storage for tickets, tenant config, and routing rules
- Admin REST endpoints for routing rules and tenant config

## Quick start (local) 🚀
1) Build
```
.\mvnw package -DskipTests
```
2) Run
```
.\mvnw spring-boot:run
```
3) (Optional) expose webhooks
```
ngrok http 8080
```

## Configuration 🔧

Required (ConnectWise)
- `company.id`
- `public.key`
- `private.key`
- `client.id`

Required (Slack)
- `slack.bot.token`
- `slack.channel.id` (default channel fallback)

Required (AWS)
- `aws.region`
- `aws.dynamodb.table`
- `aws.accessKeyId` and `aws.secretAccessKey` (or use an IAM role)

## DynamoDB (single-table design) 🧱
Table keys
- Partition key: `tenantId` (String)
- Sort key: `sk` (String)

Item types
- Tenant config
  - `sk = CONFIG`
  - fields: `slackBotToken`, `defaultChannelId`, `connectwise*`, `displayName`, etc.
- Routing rules
  - `sk = RULE#0001#<ruleId>` (zero-padded priority)
  - fields: `enabled`, `matchContact`, `matchSubject`, `matchSubjectRegex`, `targetChannelId`
- Ticket mappings
  - `sk = TICKET#<ticketId>`
  - fields: `ts_thread`, `notes`

## REST endpoints (admin) 🧩
Tenant config
- `GET /api/tenants/{tenantId}`
- `PUT /api/tenants/{tenantId}`

Routing rules
- `GET /api/tenants/{tenantId}/rules`
- `POST /api/tenants/{tenantId}/rules`
- `PUT /api/tenants/{tenantId}/rules/{ruleId}`
- `DELETE /api/tenants/{tenantId}/rules/{ruleId}?priority=NN`

## Frontend (MVP) 🎨
The frontend lives in a `frontend/` folder in this repo and calls the backend via `/api/...` endpoints.
