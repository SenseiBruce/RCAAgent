# Contributing

## Prerequisites

JDK 21 and the Maven wrapper (`./mvnw`). Node 22 if you touch `frontend/`.

## Workflow

1. Create a branch from `main`.
2. Make a focused change. Pair each `src/main/java/com/rca/agent` change with a test under `src/test/java/com/rca/agent` in the **same commit**.
3. Format and lint Java:
   ```bash
   ./mvnw fmt:format
   ./mvnw fmt:check checkstyle:check
   ```
4. Run the test suite and coverage gate:
   ```bash
   make test
   make verify
   ```
5. If you change the UI:
   ```bash
   cd frontend && npm ci && npm run lint && npm test && npm run build
   ```
6. Open a pull request. CI must pass format check, tests, frontend lint/typecheck, and dependency audit.
7. After merge, add a CHANGELOG.md entry. Tag releases (`git tag -a vX.Y.Z -m` and `git push --tags`).

## Tests

- Put tests next to the code under `src/test/java`.
- Do not call live LLM or git hosting APIs. Use `FakeLlmProvider`, Mockito, or MockWebServer.
- Keep production changes and their tests in the same commit when practical.

## Dependency audit

The GitHub Actions job **OWASP dependency-check** runs:

```bash
./mvnw -B org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7
```

The Maven plugin is also configured with `<failBuildOnCVSS>7.0</failBuildOnCVSS>`. **CVSS ≥ 7 fails the job and blocks merge.**

False positives belong in committed [`dependency-check-suppressions.xml`](dependency-check-suppressions.xml). Each suppression must cite a CVE and a reason. Review that file whenever it changes; do not use it to hide unfixed high-severity issues.

## Commit messages

Prefer conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `ci:`, `chore:`).
