# Architecture

RCA Agent is a single Spring Boot module (`com.rca:rca-agent`) with layered packages under `src/main/java/com/rca/agent`.

## Module layout

| Path | Role |
|------|------|
| `controller/` | REST API (`RcaController`) and `GlobalExceptionHandler` (Jakarta Bean Validation) |
| `chat/` | Conversational endpoint and session-oriented `ChatService` |
| `service/` | Orchestration (`RcaService`, `PromptService`) |
| `analyzer/log/` | Log parsers (plain text, JSON, CloudWatch) and summarization |
| `analyzer/git/` | JGit clone/history, time windows, repo resolution |
| `analyzer/code/` | Source snippets around stack-trace locations |
| `llm/` | `LlmProvider` SPI: OpenRouter, OpenAI, Bedrock, and **fake** (offline) |
| `fix/` | Auto-fix generation and GitHub/GitLab PR adapters |
| `config/` | `RcaProperties` bound from `application.yml` / env / `.env` |
| `model/` | Request/response records |

`frontend/` is a separate Node/Vite app. Production builds can emit into `src/main/resources/static`. `infrastructure/terraform/` deploys the API to AWS ECS Fargate.

## Request flow

```
HTTP POST /api/v1/rca/analyze
        -> RcaController (validation)
        -> RcaService
            -> LogAnalyzerService
            -> CodeContextService
            -> GitAnalyzerService / RepoResolver
            -> PromptService
            -> LlmProvider.analyze(prompt)
        -> RcaResponse JSON
```

## LLM providers

Exactly one `LlmProvider` bean is active, selected by `rca.llm.provider` / `LLM_PROVIDER`:

- `fake` — deterministic JSON, no network (tests, Docker default, `offline` Spring profile)
- `openrouter` — OpenAI-compatible HTTP client
- `openai` — OpenAI HTTP client
- `bedrock` — AWS Bedrock `InvokeModel`

Tests must not require API keys or outbound LLM calls. HTTP providers are exercised with MockWebServer; Spring tests use `fake` or `@MockitoBean LlmProvider`.

## Observability

- SLF4J + Logback (JSON via logstash-logback-encoder)
- Micrometer + Prometheus (`/actuator/prometheus`)
- Actuator health/info/metrics
