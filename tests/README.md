# Tests

The JUnit 5 suite lives at `src/test/java` (Maven Surefire). From the repo root, with **no API keys**:

```bash
make test
# or
bash scripts/test.sh
# or
bash tests/run.sh
# or
./mvnw -B test
```

Coverage gate: `make verify`.
