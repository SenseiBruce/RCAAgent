# RCA Agent — Project Charter

## Vision
An intelligent Root Cause Analysis agent that reads log files and git repositories to automatically identify the root cause of production issues.

## Tech Stack
| Component | Choice |
|-----------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.x |
| Build | Maven |
| LLM | Pluggable (Bedrock/Claude, OpenAI) |
| Git | JGit |
| Type | REST API Service |

## Architecture
- **Controller Layer** — REST endpoints for analysis requests
- **Service Layer** — RCA orchestration (log + git + LLM)
- **LLM Layer** — Pluggable provider interface (Bedrock, OpenAI, extensible)
- **Analyzer Layer** — Log parsers (JSON, plaintext, CloudWatch) + Git analysis (blame, diff, history)

## API Endpoints
- `POST /api/v1/rca/analyze` — Submit issue for root cause analysis
- `GET /api/v1/rca/health` — Health check

## Configuration
All config externalized via `application.yml` + environment variables:
- `LLM_PROVIDER` — bedrock | openai
- `AWS_REGION` — AWS region for Bedrock
- `BEDROCK_MODEL_ID` — Model to use
- `OPENAI_API_KEY` — OpenAI key (if using OpenAI)

## Phases
- [x] Phase 0: Intake & Setup
- [x] Phase 1: Core Architecture (models, interfaces, config)
- [ ] Phase 2: Full Integration Testing
- [ ] Phase 3: CloudWatch Log Parser
- [ ] Phase 4: Enhanced Git Analysis (time-window correlation)
- [ ] Phase 5: Docker + Deployment
- [ ] Phase 6: Monitoring & Observability
