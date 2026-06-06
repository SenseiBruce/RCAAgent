# Project Standards (Auto-injected into every Amazon Q chat)

## Engineering Standards

- Zero hardcoded values — all configuration externalized to config files
- Secrets via AWS Secrets Manager or environment variables, NEVER in code
- Python projects MUST use virtual environments before installing packages
- Follow SOLID principles and clean architecture patterns
- Structured logging (JSON) with levels: DEBUG, INFO, WARNING, ERROR, CRITICAL
- All external input treated as malicious — validate everything

## Code Quality

- Minimum 85% test coverage (95% for production/MVP)
- Test types: unit, integration, security (SAST/DAST), performance, accessibility (WCAG 2.1)
- Code review required before merge
- Language-specific linting enforced

## Security

- SAST on every commit
- DAST on staging deployments
- Dependency scanning weekly
- Secrets detection pre-commit
- Encryption at rest (AES-256) and in transit (TLS 1.3+)

## Configuration Hierarchy

```
config/
├── default.json          # Base config (committed)
├── development.json      # Dev overrides (committed)
├── production.json       # Prod overrides (committed)
├── local.json            # Local overrides (gitignored)
└── prompts/              # External prompt files
```

## Project Structure

```
projects/proj_YYYYMMDD_HHMMSS/
├── planning/             # Charter, timeline, tech decisions
├── docs/                 # PRD, architecture, API docs
├── design/               # Wireframes, schemas, diagrams
├── src/                  # Source code
├── infrastructure/       # Terraform, Docker, deployment
└── tests/                # Unit, integration, e2e
```

## Available Prompts (use @prompt-name in chat)

- @project-orchestrator — Master coordinator for full SDLC
- @product-manager — Requirements, PRD, user stories
- @technical-architect — System architecture, tech stack
- @backend-developer — APIs, business logic, serverless
- @frontend-developer — UI (React/Vue/Angular)
- @devops-engineer — CI/CD, infrastructure, containers
- @test-engineer — QA strategy, test automation
- @security-engineer — Threat modeling, compliance
- @database-architect — Schema design, optimization
- @engineering-copilot — Multi-role engineering assistant
