# Maintenance

RCA Agent is maintained as a small backend service with optional Terraform.

## Cadence

- **CI:** every push and pull request (format, tests, coverage, frontend lint, OWASP, Terraform validate).
- **Dependencies:** Dependabot weekly (Maven, npm, GitHub Actions, Terraform).
- **Releases:** tag `vMAJOR.MINOR.PATCH` on `main` after `./mvnw clean verify` is green. See `CHANGELOG.md`.

## Contributions

PRs should keep the production change and its tests in the same commit when practical (for example `RcaService.java` with `RcaServiceTest.java`).
