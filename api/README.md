# api

Spring Boot 3.3 backend for Calozone. Java 21, Gradle (Kotlin DSL), PostgreSQL 16.

## Run locally

```bash
docker compose -f ../infra/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Health check: http://localhost:8080/actuator/health

## Test

```bash
./gradlew build    # compile, format check, all tests (needs Docker for Testcontainers)
./gradlew spotlessApply   # fix formatting
```

## Structure

Packaged by feature: `identity`, `program`, `training`, `verdict`, `sleep`, `fuel`. Each owns its controller, service, repository and DTOs.

Migrations live in `src/main/resources/db/migration`. Add a new `V<n>__description.sql`; never edit one that has run.

## API rules

- All routes are under `/v1`.
- Within v1, changes are additive only. Removing or renaming a field needs `/v2`.
