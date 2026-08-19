# Changelog

## Unreleased

### Added

- Makefile and `scripts/test.sh` as explicit JUnit test entrypoints
- Committed Maven `dependency-tree.txt` lock snapshot with CI drift check
- Terraform fmt/validate/Checkov job and provider lockfile
- Typed `RcaException` / `AutoFixException` instead of swallowed nulls and generic fix strings

## 1.0.0-SNAPSHOT

Current feature set on `main` (tag `v1.0.0` after verify is green):

- Root cause analysis API (`POST /api/v1/rca/analyze`) with log, git, and code context
- Conversational chat UI and `POST /api/v1/rca/chat`
- Auto-fix PR/MR flow for GitHub and GitLab
- Pluggable LLM providers: OpenRouter, OpenAI, Bedrock, and offline `fake`
- Docker Compose + Dev Container one-command startup
- Actuator health/metrics, JSON logging, JaCoCo 60% gate, OWASP audit in CI
