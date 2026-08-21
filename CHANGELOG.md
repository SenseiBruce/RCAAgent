# Changelog

## Unreleased

### Fixed

- Chat no longer stores GitHub PATs in history or sends them to the LLM
- Chat auto-fix now reuses the last RCA recommendations and code snippets
- Frontend "Try again" retries the failed message instead of sending the chip text
- OWASP CI no longer fails every Dependabot PR for a missing NVD API key; job skips for Dependabot and when `NVD_API_KEY` is unset
- Terraform network module: default SG locked down, no subnet public-IP-by-default, ALB/ECS egress scoped (Checkov CKV_AWS_130/382, CKV2_AWS_12)

### Added

- Root `npm test` and Vitest `*.test.ts` so buyers detect a runnable suite at HEAD
- Offline CI job that unsets LLM/AWS credentials before `make test`
- Maven lock snapshot `dependency-tree.lock` with CI drift check and a failing-on-drift assertion
- Reusable Terraform `modules/rca-service`, encrypted S3 + DynamoDB `backend.tf`, plan/Checkov/tfsec gates
- API 400 coverage in `ValidationTest` for malformed/missing analyze, chat, and fix bodies
- Frontend `npm audit --audit-level=high` after `npm ci`
- OpenAPI spec at `docs/openapi.yaml`; Jakarta `@NotNull` / `@NotEmpty` / `@NotBlank` on API DTOs
- `VITE_API_PROXY` in `.env.example`
- JaCoCo coverage gate raised to 70%

## 1.0.0-SNAPSHOT

Current feature set on `main` (tag `v1.0.0` after verify is green):

- Root cause analysis API (`POST /api/v1/rca/analyze`) with log, git, and code context
- Conversational chat UI and `POST /api/v1/rca/chat`
- Auto-fix PR/MR flow for GitHub and GitLab
- Pluggable LLM providers: OpenRouter, OpenAI, Bedrock, and offline `fake`
- Docker Compose + Dev Container one-command startup
- Actuator health/metrics, JSON logging, JaCoCo 70% gate, OWASP audit in CI
