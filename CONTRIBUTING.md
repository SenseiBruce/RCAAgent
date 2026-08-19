# Contributing

## Prerequisites

JDK 21 and the Maven wrapper (`./mvnw`). Node 22 if you touch `frontend/`.

## Workflow

1. Create a branch from `main`.
2. Make a focused change.
3. Format and lint Java:
   ```bash
   ./mvnw fmt:format
   ./mvnw fmt:check checkstyle:check
   ```
4. Run the test suite and coverage gate:
   ```bash
   ./mvnw clean verify
   ```
5. If you change the UI:
   ```bash
   cd frontend && npm ci && npm run lint && npm run build
   ```
6. Open a pull request. CI must pass format check, tests, frontend lint/typecheck, and dependency audit.

## Tests

- Put tests next to the code under `src/test/java`.
- Do not call live LLM or git hosting APIs. Use `FakeLlmProvider`, Mockito, or MockWebServer.
- Keep new files under 500 lines.

## Commit messages

Prefer conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `ci:`, `chore:`).
