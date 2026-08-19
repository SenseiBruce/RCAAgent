# Tests

The JUnit 5 suite lives at `src/test/java` (Maven Surefire). From the repo root:

```bash
make test
# or
bash scripts/test.sh
# or
./mvnw -B test
```

No API keys or network LLM calls are required. Coverage gate: `make verify`.
