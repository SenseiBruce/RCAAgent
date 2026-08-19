# Changelog

## Unreleased

### Added

- Dedicated CI **Test** job running `make test` (JUnit 5)
- Maven lock snapshot `dependency-tree.lock` with CI drift check
- Terraform moved to `deploy/terraform` with provider lockfile, `terraform plan`, Checkov, and tfsec
- OpenAPI spec at `docs/openapi.yaml`; Jakarta `@NotNull` / `@NotEmpty` / `@NotBlank` on API DTOs
- `VITE_API_PROXY` in `.env.example`

## 1.0.0-SNAPSHOT

Current feature set on `main` (tag `v1.0.0` after verify is green):

- Root cause analysis API (`POST /api/v1/rca/analyze`) with log, git, and code context
- Conversational chat UI and `POST /api/v1/rca/chat`
- Auto-fix PR/MR flow for GitHub and GitLab
- Pluggable LLM providers: OpenRouter, OpenAI, Bedrock, and offline `fake`
- Docker Compose + Dev Container one-command startup
- Actuator health/metrics, JSON logging, JaCoCo 60% gate, OWASP audit in CI
