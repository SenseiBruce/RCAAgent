# Tests

The JUnit 5 suite lives at `src/test/java` (Maven Surefire). From the repo root, with **no API keys**:

```bash
make test
# or
bash tests/test_rca.sh
# or
./mvnw -B test
# or, from repo root
npm test
```

Frontend Vitest: `cd frontend && npm test`.

Coverage gate: `make verify` (JaCoCo 70%).
