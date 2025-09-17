# SlackWise 🚀

A lightweight Spring Boot bridge that syncs ConnectWise service tickets and notes with a Slack channel — built for fast iteration and real-world integrations. ⚡️

## Goal 🎯
Make it easy to respond to tickets by connecting ConnectWise and Slack with durable mappings in DynamoDB, allowing engineers and customers to collaborate seamlessly.

## Prerequisites ✅
- Java 17 JDK
- Maven (or use included mvnw) 🛠️
- AWS account with DynamoDB permissions (or DynamoDB Local for dev) ☁️🗄️
- ConnectWise API access (company id, public/private keys, clientId) 🔑
- Slack workspace + Slack App (bot token, channel id, signing secret) 💬
- (Optional) ngrok for exposing local webhooks during development 🔌

## What you need (secrets & settings) 🔐
Edit `src/main/resources/application.properties` or use environment variables.

Required properties:
- company.id = `<ConnectWise company id, e.g. 19300>`
- public.key = `<ConnectWise public API key>`
- private.key = `<ConnectWise private API key>`
- client.id = `<ConnectWise clientId header>`
- slack.bot.token = `xoxb-...` (bot token)
- slack.channel.id = `C0123456789` (channel to post tickets)
- aws.* = AWS credentials/config or use an IAM role
- (Optional) baseUrl override if your tenant uses a different API version

Do NOT commit secrets to source control. 🚫🔒

## DynamoDB setup 🗄️
Create a table (example name: `TicketThreadMapping`) with:
- Partition key: `ticketId` (String)
- Attributes: `ts_thread` (String), `notes` (List/Set)
Ensure the app's AWS credentials allow PutItem/GetItem/UpdateItem/ConditionalWrite.

## Slack app setup basics 🧩
1. Create a Slack App in your workspace.
2. Add OAuth scopes:
   - chat:write, users:read, channels:read, chat:write.public (as needed)
3. Install app and copy Bot Token.
4. Enable Event Subscriptions → set Request URL to `/slack/events` (use ngrok locally).
5. Subscribe to message events (but ensure the app ignores bot messages to avoid loops).
6. Save the signing secret if you enable verification.

## ConnectWise setup basics 🔗
- Create an API member and generate public/private keys.
- Record company id and clientId header value.
- Prefer using `ticket._info.*` links from webhooks to avoid API-version mismatches.
- Setup API Callbacks

## Running locally (Windows) ▶️
1. Build:
   ```
   .\mvnw package -DskipTests
   ```
2. Run:
   ```
   .\mvnw spring-boot:run
   ```
   or
   ```
   java -jar target\*.jar
   ```
3. Expose webhooks with ngrok:
   ```
   ngrok http 8080
   ```
