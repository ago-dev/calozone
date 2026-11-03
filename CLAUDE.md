# CLAUDE.md

Calozone: a training app that turns sleep, food and training into one daily verdict — how hard to train today and how much to eat. Monorepo: iOS + Android app and an API.

---

## 1. Ground rules (read first)

1. **Never write without approval.** Before creating, editing or deleting any file — code, config, docs, migrations — present a plan and wait for an explicit "yes", "go" or "approved". Exceptions: only when the user says so for that task (e.g. "just do it", "no need to ask").
2. **Clean code is the bar, not a bonus.** Follow section 4 for the language you're in. If a request would force a violation, say so and propose the clean alternative.
3. **Document as you write.** Section 5 defines what must be documented. Undocumented public code is unfinished.
4. **Never** push to `main`, force-push, merge PRs, or read/print/edit/commit any `.env` file.

Reading files, running read-only commands (`git status`, `git log`, `ls`, `docker compose ps`) and running builds/tests to investigate do **not** need approval.

---

## 2. Stack

| Area | Language | Framework / tools |
|---|---|---|
| `api/` | Java 21 | Spring Boot 4.1, Spring Security, Spring Data JPA (Hibernate), Bean Validation, Flyway, JUnit 5, Testcontainers, WireMock |
| Database | SQL (PostgreSQL 16) | Flyway migrations, jsonb, window functions |
| Build | Kotlin (Gradle Kotlin DSL) | Gradle version catalog, Spotless, Error Prone, Jib |
| `app/` | TypeScript (strict) | React Native via Expo, expo-router, expo-sqlite, EAS, Jest, Maestro |
| `infra/` | YAML | docker-compose, GitHub Actions |

---

## 3. Workflow for a roadmap step

`docs/roadmap.md` is the source of truth. Steps are `stage.step` in execution order.

When asked to "do step X.YY":

1. **Read** the step's Details in `docs/roadmap.md`. "Done when" is the acceptance test.
2. **Check dependencies.** If earlier steps it relies on aren't ticked, say so and stop.
3. **Propose a plan** and wait for approval:
   - files to create / change / delete
   - key design decisions (classes, interfaces, tables, endpoints) and why
   - how "Done when" will be verified
   - anything in the roadmap that looks wrong or outdated
4. **On approval:** `git switch main && git pull`, then `git switch -c <area>/<short-name>` (area: `infra`, `db`, `api`, `app`, `docs`).
5. **Implement only the approved plan.** If something unplanned is needed, stop and ask.
6. **Verify:** run the checks in section 7 until green.
7. **Tick** the step in `docs/roadmap.md` in the same branch.
8. **Show the diff summary** and ask before committing and pushing.
9. **Open the PR** with `gh pr create`: title `X.YY: <step title>`; body = what changed, why, how "Done when" was verified.
10. **Stop.** The user reviews and merges.

Small commits with imperative messages: `Add refresh token rotation`, not `changes`.

---

## 4. Clean code by language

### General (all languages)

- **Single responsibility.** One reason to change per class, function, module and file.
- **Names say what, not how.** `findActiveProgram`, not `getData`. No abbreviations except well-known ones (`id`, `url`, `dto`). Booleans read as questions: `isComplete`, `hasConsent`.
- **Small functions.** One level of abstraction per function. If you need a comment to separate sections inside a function, extract them.
- **No magic values.** Numbers and strings with meaning become named constants or config.
- **Fail fast and explicitly.** Validate at the boundary; inside, trust the types. Never swallow exceptions or errors silently.
- **Don't repeat knowledge** (DRY) — but don't abstract until the second real use (YAGNI). Three similar lines beat a premature abstraction.
- **Immutability by default.** Mutate only where it is the clearest option.
- **Pure core, impure edges.** Business rules are pure functions; I/O (DB, HTTP, clock, device APIs) lives at the edges and is injected.
- **Tests are code.** Same naming and clarity standards. One behaviour per test; name it after the behaviour.
- **No dead code, no commented-out code, no TODO without a roadmap step number.**

### Java 21 / Spring Boot — object-oriented with functional core

- **Package by feature**, not layer: `app.calozone.<feature>` with `identity`, `program`, `training`, `verdict`, `sleep`, `fuel`, plus `common` for cross-cutting pieces. Each feature owns its controller, service, repository, entities and DTOs. Cross-feature access goes through the other feature's service, never its repository.
- **Layers inside a feature:** `Controller` (HTTP only: map request → call service → map response) → `Service` (use cases, transactions) → `Repository` (persistence). Domain rules that don't need I/O live in plain classes with no Spring annotations (e.g. `VerdictRules`).
- **Constructor injection only.** Fields `private final`. No `@Autowired` on fields.
- **Records** for DTOs, value objects and inputs (`VerdictInputs`). **Sealed interfaces** + pattern-matching `switch` for closed sets of outcomes.
- **Entities** are persistence models: no business logic beyond invariants, never returned from controllers. Map to DTOs explicitly.
- **`Optional`** only as a return type for "may be absent". Never as a field or parameter. Never return `null` from a public method.
- **Exceptions:** domain exceptions extend one base (`CalozoneException`) carrying a stable `ErrorCode`. One `@RestControllerAdvice` maps them to `ProblemDetail`. No `catch (Exception e)` except at that boundary.
- **Transactions** on service methods (`@Transactional`), read-only where applicable. Never on controllers or repositories.
- **Time** comes from an injected `Clock`. Never call `LocalDate.now()` or `Instant.now()` directly.
- **Configuration** through `@ConfigurationProperties` records, validated at startup. No `@Value` scattered through classes.
- **SOLID** applies: depend on interfaces at boundaries you'll swap or fake (`VisionClient`, `SleepSource` on the app side); don't create interfaces for classes with one implementation and no test need.
- **Tests:** JUnit 5 + AssertJ. Unit tests for pure logic (`@ParameterizedTest` for rule tables). Integration tests extend the Testcontainers base class. Name: `methodOrBehaviour_condition_expectedResult`.

### SQL / PostgreSQL — declarative, set-based

- One migration = one logical change. File: `V<n>__<verb>_<what>.sql` (e.g. `V4__add_version_columns.sql`).
- **Never edit a migration on `main`.** Add a new one.
- `snake_case` for everything. Tables plural (`food_logs`), columns singular. Primary key `id uuid`. Foreign keys `<table_singular>_id`.
- Every foreign key has an index and an explicit `ON DELETE` rule.
- Constraints in the database, not only in Java: `NOT NULL`, `CHECK`, `UNIQUE`, partial unique indexes for business rules.
- `timestamptz` in UTC for instants; `date` for user-local days (`local_date`).
- Think in sets: aggregates and window functions in SQL rather than loops in Java.
- Every query filters by `user_id`. Check new screen queries with `EXPLAIN ANALYZE`.
- Hibernate runs with `ddl-auto=validate`. Never `update` or `create`.

### TypeScript / React Native (Expo) — functional, declarative UI

- **`strict: true`**, no `any`, no non-null `!` assertions without a comment explaining why it's safe. Prefer `unknown` + narrowing.
- **Function components and hooks only.** No class components.
- **Separate UI from logic:** screens compose; hooks (`useTodayVerdict`) hold state and effects; plain modules (`outbox/`, `sync/`, `domain/`) hold logic with no React imports so they are unit-testable.
- **Folder by feature** under `app/src/features/<feature>/` (`components/`, `hooks/`, `api.ts`, `types.ts`). Shared UI in `app/src/ui/`. Routes in `app/app/` stay thin: they import a screen and render it.
- **Types:** `type` for data shapes, discriminated unions for states (`{ status: 'loading' } | { status: 'ready'; verdict: Verdict }`). Make impossible states unrepresentable.
- **API types** come from the generated client only. Never hand-write request/response types.
- **Every write** goes to SQLite + outbox first. The UI never awaits the network to record something.
- **Styles** from `theme.ts` tokens only. No hard-coded colors, sizes or font names. `StyleSheet.create` at the bottom of the component file.
- **Effects** declare complete dependency arrays; no disabling `react-hooks/exhaustive-deps`.
- **Naming:** components `PascalCase.tsx`, hooks `useCamelCase.ts`, other modules `camelCase.ts`. One exported component per file.
- **Tests:** Jest for logic modules and hooks; Maestro for end-to-end flows. Name tests as sentences: `it('retries with backoff after a 5xx')`.

### Gradle Kotlin DSL

- All versions in `gradle/libs.versions.toml`. No version strings in `build.gradle.kts`.
- Build logic declarative and short; anything reusable goes in a convention plugin, not copy-paste.

### YAML (docker-compose, GitHub Actions)

- Pin versions: image tags exact (`postgres:16.15`), actions by major version or SHA.
- Name every job and step so logs read as sentences.
- No secrets inline; GitHub Actions secrets or env files only.
- Comment non-obvious settings on the line above.

---

## 5. Documentation

Write documentation in the same change as the code. Explain **why** and **contracts**, not what the code literally does.

**Java — Javadoc** on:
- every public class, record, interface and enum: one sentence on its responsibility, plus anything a caller must know (thread safety, transaction expectations).
- every public method: what it does, `@param` constraints, `@return` meaning, `@throws` for each domain exception.
- `package-info.java` per feature package: what the feature owns and its boundaries.
- Private methods only when the intent isn't obvious from the name.

**TypeScript — TSDoc** (`/** */`) on:
- every exported function, hook, component and type: purpose, params, return, side effects (network, storage, permissions).
- module header comment for logic modules (`outbox/`, `sync/`) explaining the design.

**SQL** — a header comment in each migration: what it changes and why. `COMMENT ON TABLE` / `COMMENT ON COLUMN` for anything whose meaning isn't obvious (e.g. `inputs_hash`, `superseded_by`).

**Inline comments** only for non-obvious reasons: a workaround, a performance choice, a business rule with a source. Never narrate code.

**READMEs:** update `api/README.md`, `app/README.md` or `infra/README.md` when setup, commands or structure change.

**API:** OpenAPI annotations (`@Operation`, `@Schema`) on every endpoint and DTO. Regenerate and commit `api/openapi.json`.

---

## 6. Architecture rules

- All routes under `/v1`. Within v1, changes are additive only.
- Errors are `ProblemDetail` with a stable `code`.
- Every client write is idempotent on `client_uuid` (`ON CONFLICT DO NOTHING`, return the existing row).
- Every query filters by the authenticated user. Other users' ids return 404.
- Verdict rules are pure (`VerdictRules`): no Spring, no clock, no I/O.
- Meal photos are never stored server-side: in memory → vision API → discarded.
- Health data is GDPR special-category data. Never log emails, names, tokens, weights, kcal, sleep values or food names. Log ids only.
- Analytics events carry counts and enums only.

---

## 7. Commands and checks

```bash
# Database (from infra/)
docker compose up -d
docker compose ps                       # wait for "healthy"
docker exec -it calozone-postgres psql -U calozone

# API (from api/)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
./gradlew build                         # compile + format check + tests
./gradlew spotlessApply

# App (from app/)
npx expo start
npx tsc --noEmit && npx eslint . && npm test
npm run gen:api                         # after any API change
```

Before any PR: `./gradlew build` in `api/`, and `tsc + eslint + test` in `app/`, for whichever side changed. Never open a PR with a failing check.

---

## 8. Local machine

- macOS, Docker Desktop.
- Postgres runs in Docker on `127.0.0.1:5432`. An old EnterpriseDB Postgres was disabled to free that port; if 5432 is busy, check `sudo lsof -nP -iTCP:5432 -sTCP:LISTEN`.
- The database needs a few seconds after `docker compose up -d` before it accepts connections.

---

## 9. Design

Screens, tokens and specs live in `docs/design/`. The design is final: implement it as specified. Raise design questions with the user instead of improvising.
