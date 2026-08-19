# RCA Agent

Root Cause Analysis Agent is a Spring Boot service that analyzes logs and git history, then uses a pluggable LLM to identify likely root causes and optional auto-fix pull requests.

## Requirements

- JDK 21+
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- Docker and Docker Compose (optional, for isolated startup)
- Node.js 22+ (optional, for the chat UI)

No live LLM, AWS, or GitHub credentials are required to **build, test, or start** the app. Use `LLM_PROVIDER=fake` (the Docker default) for fully offline runs.

## Install, build, and test (fresh clone)

```bash
git clone <this-repo-url> RCAAgent
cd RCAAgent
cp .env.example .env   # optional; not needed for tests

# Run the full test suite, coverage report, and coverage gate
./mvnw clean verify
```

Expected result: Maven exits `0`. Coverage HTML is written to `target/site/jacoco/index.html`.

Other useful commands:

| Command | What it does |
|---------|----------------|
| `./mvnw test` | Unit and integration tests only |
| `./mvnw clean verify` | Tests + JaCoCo report + **60% coverage check** |
| `./mvnw fmt:check` | Google Java Format check (fails on unformatted files) |
| `./mvnw fmt:format` | Apply Google Java Format |
| `./mvnw checkstyle:check` | Checkstyle lint |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=offline` | Run API locally with the fake LLM |

CI runs `./mvnw fmt:check` and `./mvnw clean verify` on every push and pull request.

## One-command Docker startup

From a clean clone (no API keys required):

```bash
docker compose up --build
```

This starts:

- **rca-agent** on [http://localhost:8080](http://localhost:8080) with `LLM_PROVIDER=fake`
- **frontend** on [http://localhost:3000](http://localhost:3000), proxied to the API

Health checks:

- App: `GET http://localhost:8080/api/v1/rca/health`
- Actuator: `GET http://localhost:8080/actuator/health`

To use a real provider, copy `.env.example` to `.env`, set keys, and set `LLM_PROVIDER=openrouter` (or `openai` / `bedrock`) in that file — or export it — then `docker compose up`. Compose still activates the `offline` profile by default; that profile now honors `LLM_PROVIDER` (default `fake`) instead of forcing the fake bean.

## Configuration

See [`.env.example`](.env.example) for every environment variable. Common values:

| Variable | Purpose | Default |
|----------|---------|---------|
| `LLM_PROVIDER` | `fake`, `openrouter`, `openai`, or `bedrock` | `openrouter` without a profile; `fake` when `offline` is active unless `LLM_PROVIDER` is set |
| `OPENROUTER_API_KEY` | OpenRouter key | empty |
| `OPENAI_API_KEY` | OpenAI key | empty |
| `AWS_REGION` | Bedrock region | `us-east-1` |
| `GITHUB_TOKEN` | Optional; needed only for auto-fix PRs | empty |

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/rca/analyze` | Root cause analysis (`issueDescription` required) |
| `POST` | `/api/v1/rca/chat` | Conversational RCA |
| `POST` | `/api/v1/rca/fix` | Generate a fix PR (`X-GitHub-Token` header) |
| `GET` | `/api/v1/rca/health` | Liveness string |
| `GET` | `/actuator/health` | Spring Actuator health |
| `GET` | `/actuator/prometheus` | Metrics |

Minimal analyze request (works with the fake provider):

```bash
curl -s http://localhost:8080/api/v1/rca/analyze \
  -H 'Content-Type: application/json' \
  -d '{"issueDescription":"NullPointerException on login","logContent":"ERROR NPE at UserService.java:42"}'
```

## Project layout

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for package layering (`controller`, `service`, `analyzer`, `llm`, `fix`).

```
src/main/java/com/rca/agent/   Java API
src/test/java/                 JUnit 5 + MockMvc tests (no live network)
frontend/                      React + Vite UI
infrastructure/terraform/      Optional AWS deploy
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Please keep Java formatted (`./mvnw fmt:format`) and tests green (`./mvnw clean verify`).
