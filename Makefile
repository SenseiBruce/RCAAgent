.PHONY: test verify build fmt lint

# Runnable test suite (JUnit 5 via Maven Surefire). No API keys required.
test:
	./mvnw -B test

verify:
	./mvnw -B clean verify

build:
	./mvnw -B -DskipTests package

fmt:
	./mvnw -B fmt:check

lint:
	./mvnw -B checkstyle:check
