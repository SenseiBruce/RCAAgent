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

`frontend/` is a separate Node/Vite app. Production builds can emit into `src/main/resources/static`. Optional AWS deploy lives in `deploy/terraform/` and is not part of the Java service runtime.

## Runtime dependencies

Declared in root `pom.xml`. Inspect the resolved graph without a build via committed [`dependency-tree.txt`](../dependency-tree.txt) (refreshed with `./mvnw dependency:tree -DoutputFile=dependency-tree.txt`; CI also checks [`dependency-tree.lock`](../dependency-tree.lock)).

| Artifact | Purpose |
|----------|---------|
| `spring-boot-starter-web` | REST API and embedded Tomcat |
| `spring-boot-starter-validation` | Jakarta `@Valid` / `@NotBlank` on request bodies |
| `software.amazon.awssdk:bedrockruntime` | Optional Bedrock LLM provider |
| `org.eclipse.jgit` | Clone and inspect git history |
| `jackson-databind` / `jackson-datatype-jsr310` | JSON request/response mapping |
| `spring-boot-starter-webflux` | HTTP client for OpenAI / OpenRouter |
| `logstash-logback-encoder` | JSON logs |
| `spring-boot-starter-actuator` + Micrometer Prometheus | Health and metrics |

Lombok is compile-only. Test-only artifacts (`spring-boot-starter-test`, `junit-jupiter`, MockWebServer) are `test` scope.

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
