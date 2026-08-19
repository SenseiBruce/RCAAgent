# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY checkstyle.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests -B -Dfmt.skip=true && \
    mv target/rca-agent-*.jar target/app.jar

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget \
    && addgroup -S appgroup \
    && adduser -S appuser -G appgroup

COPY --from=build /app/target/app.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/rca/health || exit 1

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
