# api

Spring Boot 4.1 backend for Calozone. Java 21, Gradle (Kotlin DSL), PostgreSQL 16.

## Run locally

```bash
docker compose -f ../infra/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Health check: http://localhost:8080/actuator/health

`bootRun` reads the database password from `infra/.env` (copy `infra/.env.example` first). Until migrations exist (roadmap 0.06), the app starts but has no tables.

Gradle uses a Java 21 toolchain. If your default JDK is newer, Gradle finds an installed JDK 21 on its own.

## Profiles

| Profile | Used for | Database settings come from |
|---|---|---|
| `local` | Your machine | `infra/.env` (git-ignored) |
| `test` | Automated tests | Testcontainers (0.07) |
| `prod` | Real servers | Env vars `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |

`application.yml` holds shared defaults; `application-<profile>.yml` overrides them. Never commit a secret: config files hold placeholders like `${DB_PASSWORD}` only.

## Test

```bash
./gradlew build    # compile, format check, all tests (needs Docker for Testcontainers)
./gradlew spotlessApply   # fix formatting
```

The build is strict on purpose:

- **Formatting:** Spotless checks Java (google-java-format) and `*.gradle.kts` (ktfmt). Run `./gradlew spotlessApply` before committing; an unformatted file fails the build.
- **Static analysis:** Error Prone runs on every compile and turns common bug patterns into errors.
- **No warnings:** the compiler runs with `-Xlint:all -Werror`, so any warning fails the build.

Dependency and plugin versions live in `gradle/libs.versions.toml`. Don't put version strings in `build.gradle.kts`.

### Integration tests

Tests that need the application and a database extend `IntegrationTest`. It starts one real PostgreSQL (`postgres:16.15`, same as `infra/`) in Docker for the whole test run and hands its connection to Spring via `@ServiceConnection`, under the `test` profile. Docker must be running; nothing else is needed. `HealthSmokeTest` checks that the context loads, Flyway migrates and the database answers.

## Structure

Packaged by feature: `identity`, `program`, `training`, `verdict`, `sleep`, `fuel`. Each owns its controller, service, repository and DTOs.

Migrations live in `src/main/resources/db/migration`. Add a new `V<n>__description.sql`; never edit one that has run.

Hibernate runs with `ddl-auto: validate`: it never changes the schema, it only checks at startup that entities match it.

## API rules

- All routes are under `/v1`.
- Within v1, changes are additive only. Removing or renaming a field needs `/v2`.
