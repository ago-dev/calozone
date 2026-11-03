# Calozone implementation roadmap

Spring Boot 4.1 (Java 21) · PostgreSQL 16 · Expo (React Native, TypeScript)

Steps are numbered in execution order: `stage.step`. Each step assumes the ones before it are merged. One step per branch and pull request; tick the box in the same PR.

Branch names: `infra/…`, `db/…`, `api/…`, `app/…` followed by a short description.

## Where data lives

- **PostgreSQL** — all tables. Semi-structured fields (verdict reasoning, sleep intervals) use `jsonb`.
- **SQLite on device** — offline write queue (outbox) and cached verdict.
- **Meal photos** — not stored. Sent with the estimate request, forwarded to the vision API in memory, discarded. Thumbnails stay on the phone.
- **Redis** — not needed. Only if several API instances must share rate limits.

## Conventions

- **Package by feature:** identity, program, training, verdict, sleep, fuel. Each owns its controller, service, repository and DTOs.
- **Migrations only:** Flyway owns the schema. Hibernate runs with `ddl-auto=validate`. Never edit a migration that has run anywhere shared.
- **Idempotent writes:** every client write carries `client_uuid`. Insert with `ON CONFLICT DO NOTHING` and return the existing row.
- **Local dates:** days are keyed by the user's `local_date` plus stored timezone. Timestamps are `timestamptz` in UTC.
- **API:** all routes under `/v1`; additive changes only within a version.

## Progress

3 of 101 steps done.

| Stage | Name | Steps |
|---|---|---|
| 0 | Environment | 13 |
| 1 | Foundations | 17 |
| 2 | Logging core | 22 |
| 3 | The verdict | 13 |
| 4 | Sleep sync | 8 |
| 5 | Fuel & photos | 11 |
| 6 | Progress | 7 |
| 7 | Hardening & beta | 10 |

---

# Start

_Stages 0–2 · stack, plumbing, plain logging_

## Stage 0 — Environment

Module: repo · CI · local stack

**Milestone:** Empty service deployed to staging from a merge to main

- [x] **0.01** `Infra` Monorepo: api/, app/, infra/. Protect main: PR + green CI required
- [x] **0.02** `Infra` docker-compose: Postgres 16 (pinned tag, named volume, port 5432)
- [x] **0.03** `Backend` Spring Initializr: Boot 4.1, Java 21, Gradle Kotlin DSL; web, security, data-jpa, validation, actuator, flyway
- [x] **0.04** `Backend` Gradle version catalog; Spotless + Error Prone; warnings fail the build
- [ ] **0.05** `Backend` application.yml per profile (local, test, prod); secrets from env vars only
- [ ] **0.06** `Database` Flyway wired; V1 = the 13-table handoff schema; ddl-auto=validate
- [ ] **0.07** `Backend` Testcontainers base class (reused container) + smoke test on /actuator/health
- [ ] **0.08** `Infra` GitHub Actions: build, test, Jib image push on main
- [ ] **0.09** `Infra` Dependabot for Gradle, npm and Actions; CodeQL scan on PRs
- [ ] **0.10** `Infra` Staging host + managed Postgres in an EU region; deploy the empty service
- [ ] **0.11** `App` Expo with dev client, TypeScript strict, ESLint
- [ ] **0.12** `App` EAS build profiles: development, preview, production
- [ ] **0.13** `App` EAS Update channels per profile; runtimeVersion policy

### 0.01 — Monorepo: api/, app/, infra/. Protect main: PR + green CI required

_Infra_

**What**

A monorepo is one Git repository that holds every part of the product. Instead of three repos (backend, app, infrastructure) you have one, with a folder for each:

api/ — the Spring Boot backend.  
app/ — the Expo (React Native) mobile app.  
infra/ — everything about running the system: docker-compose for local Postgres, deployment config, CI scripts.

**Why one repo**

The app and the API change together. When you add a field to an endpoint, the backend change, the regenerated API client and the screen that uses it land in one pull request and one review. With separate repos you coordinate versions by hand and they drift. For a team of one to three people a monorepo removes that overhead.

**Protect main**

"main" is the branch that represents what is deployable. Protecting it is a GitHub setting (Settings → Branches → Add rule) that blocks pushing to it directly.

"PR required" means every change goes through a pull request, even yours, so there is a reviewable record of what changed and why.

"Green CI required" means GitHub refuses the merge until the automated pipeline (build + tests, set up in 0.08) has passed — the green check mark. A broken build can never reach main, so main is always safe to deploy.

**How**

Create the repo with a README and .gitignore (Java, Node, macOS). Add the three folders with a short README each. Add the branch rule now with "Require a pull request" ticked; tick "Require status checks" after 0.08 exists, because there is no check to require yet.

**Done when**

A direct git push to main is rejected, and a PR can be merged.

### 0.02 — docker-compose: Postgres 16 (pinned tag, named volume, port 5432)

_Infra_

**What**

docker-compose is a file (infra/docker-compose.yml) that starts services in containers with one command: docker compose up -d. Here it runs PostgreSQL 16 on your machine so every developer has the same database without installing Postgres.

**Pinned tag**

Use an exact image tag like postgres:16.4, not postgres:latest. "latest" changes under you and a teammate can end up on a different major version than production.

**Named volume**

A volume is where the container keeps its data files. A named volume (e.g. pgdata) survives docker compose down, so you don't lose local data each restart. docker compose down -v wipes it when you want a clean slate.

**Port 5432**

Postgres' default port, mapped to your machine so the Spring app and tools like psql or DBeaver connect at localhost:5432.

**Done when**

docker compose up -d starts, and psql -h localhost -U calozone connects.

### 0.03 — Spring Initializr: Boot 4.1, Java 21, Gradle Kotlin DSL; web, security, data-jpa, validation, actuator, flyway

_Backend_

**What**

Spring Initializr (start.spring.io, or built into IntelliJ) generates the starting backend project in api/.

**Choices**

Spring Boot 4.1 — current supported line (3.x is out of open-source support); requires Java 17+ and Gradle 9.  
Java 21 — the current long-term-support release; gives records, pattern matching and virtual threads.  
Gradle Kotlin DSL — build file written in Kotlin (build.gradle.kts), which gives autocomplete and type errors in the IDE. Maven would also work; pick one and stay.

**Dependencies**

web — REST controllers and the embedded Tomcat server.  
security — authentication and authorization (configured in 1.08).  
data-jpa — Hibernate + Spring Data repositories for talking to Postgres.  
validation — @NotNull, @Size etc. on request bodies.  
actuator — /actuator/health and metrics endpoints for monitoring.  
flyway — runs SQL migrations on startup.  
Also add the postgresql JDBC driver.

**Where it goes**

The generated project becomes the api/ folder itself, not a subfolder inside it.

Web: on start.spring.io set Artifact = api, click Generate, and unzip api.zip at the repo root. The zip already contains an api/ folder; if you created api/ with a README in 0.01, merge into it. Result: api/build.gradle.kts, api/gradlew, api/src/…

IntelliJ: New Project → Spring Boot, Location = <repo>/api.

Run Gradle from inside api/ (cd api && ./gradlew build). In CI, set working-directory: api. Don't run git init in api/; the repo root is the only Git repo.

**Package**

Group app.calozone, artifact api. Package by feature (identity, program, training, verdict, sleep, fuel), not by layer.

**Done when**

./gradlew bootRun starts (it will fail to connect to a DB until 0.05/0.06 — that's expected).

### 0.04 — Gradle version catalog; Spotless + Error Prone; warnings fail the build

_Backend_

**What**

Build hygiene, set up before any real code so it never needs a cleanup pass.

**Version catalog**

gradle/libs.versions.toml lists every dependency version in one file. Upgrades become one-line changes and versions can't silently differ between modules.

**Spotless**

Auto-formats code (google-java-format or palantir). ./gradlew spotlessApply fixes formatting; spotlessCheck fails CI if someone didn't run it. No formatting debates in reviews.

**Error Prone**

A Google compiler plugin that turns common bugs (wrong equals, ignored return values, misused Optional) into compile errors.

**Warnings fail the build**

Add -Werror to the Java compiler options. Warnings that are allowed pile up until nobody reads them.

**Done when**

./gradlew build passes clean, and a deliberately misformatted file makes it fail.

### 0.05 — application.yml per profile (local, test, prod); secrets from env vars only

_Backend_

**What**

A Spring profile is a named set of configuration. You'll have three: local (your machine + compose Postgres), test (Testcontainers), prod (the real servers).

**How**

application.yml holds shared defaults. application-local.yml, application-test.yml and application-prod.yml override per environment. Pick one with SPRING_PROFILES_ACTIVE=local.

Write secrets as placeholders: spring.datasource.password: ${DB_PASSWORD}. The real value comes from an environment variable — a local .env file (git-ignored) on your machine, the host's secret store in prod.

**Why env only**

A password committed to Git stays in history forever, even after deletion. Env vars keep secrets out of the repo and let each environment have its own.

**Done when**

The app starts under the local profile and connects to compose Postgres, with no secret in any committed file.

### 0.06 — Flyway wired; V1 = the 13-table handoff schema; ddl-auto=validate

_Database_

**What**

Flyway is a migration tool: you write numbered SQL files (V1__init.sql, V2__…) in api/src/main/resources/db/migration, and on startup it applies any that haven't run yet, recording each in a flyway_schema_history table.

**Why**

The schema becomes versioned code. Every environment — your laptop, CI, staging, prod — reaches the exact same schema by replaying the same files in order. Nobody edits a database by hand.

**V1**

Copy design_handoff_calozone/backend/V1__initial_schema.sql to db/migration/V1__initial_schema.sql. It creates the 13 tables: users, profiles, exercises, programs, program_days, program_exercises, day_verdicts, sessions, set_logs, sleep_nights, food_logs, saved_meals, bodyweight. Using the real schema from the first migration means V1 never has to be edited after it has run somewhere.

**Rule**

Never edit a migration that has run anywhere shared. To change something, add a new V-file. Flyway checksums old files and refuses to start if one changed.

**ddl-auto=validate**

Hibernate can create or alter tables from your Java entities (ddl-auto=update). Never let it: the schema must come only from Flyway. "validate" makes Hibernate check at startup that every entity matches a real table and column, and refuse to start if not. It catches mapping mistakes immediately.

**Done when**

Startup logs "Successfully applied 1 migration", the 13 tables exist, and the app starts with ddl-auto=validate (it passes trivially until entities arrive in 1.02).

### 0.07 — Testcontainers base class (reused container) + smoke test on /actuator/health

_Backend_

**What**

Testcontainers starts a real Postgres in Docker for the duration of your tests. A base class (e.g. IntegrationTest) that all database tests extend owns that container.

**Why real Postgres**

H2 or other in-memory databases don't support jsonb, partial unique indexes or ON CONFLICT the way Postgres does. Tests against them pass while production fails. Testing against the same engine removes that class of bug.

**Reused container**

Starting Postgres takes a few seconds. Declare the container static (and use @ServiceConnection in Boot 3.1+) so one container serves the whole test run instead of one per class.

**Smoke test**

One @SpringBootTest that calls GET /actuator/health and asserts status UP. It proves the app context loads, Flyway runs, and the DB connects — the foundation every later test relies on.

**Done when**

./gradlew test runs green with Docker running and nothing else installed.

### 0.08 — GitHub Actions: build, test, Jib image push on main

_Infra_

**What**

GitHub Actions runs a workflow file (.github/workflows/api.yml) on every pull request and every push to main.

**Steps**

On PR: check out, set up Java 21 with Gradle caching, run ./gradlew build (compile, format check, tests). GitHub runners have Docker, so Testcontainers works.

On main: same, then build and push a container image.

**Jib**

A Gradle plugin that builds an optimized Docker image of the Spring app without a Dockerfile or Docker daemon. Push it to GitHub Container Registry (ghcr.io) tagged with the commit SHA, so every deployed version traces back to an exact commit.

**After this**

Go back to the branch rule from 0.01 and require this workflow's check. That is what "green CI required" enforces.

**Done when**

A PR shows a green check, and merging produces an image in ghcr.io.

### 0.09 — Dependabot for Gradle, npm and Actions; CodeQL scan on PRs

_Infra_

**What**

Dependabot (built into GitHub) opens pull requests when a dependency has a new version or a known vulnerability. CodeQL is GitHub's static analysis: it scans the Java and TypeScript code for security bugs such as SQL injection or unsafe deserialization.

**How**

Add .github/dependabot.yml with three ecosystems: gradle (directory /api), npm (/app), github-actions (/). Weekly schedule; group minor and patch updates into one PR so you get one review instead of twenty. Turn on CodeQL default setup in Settings → Code security.

**Why now**

Updating a little every week is cheap; updating a year of dependencies at once is a project. For an app holding health data, a vulnerable library is a real risk, and the CI from 0.08 already tells you whether an update is safe to merge.

**Done when**

The first Dependabot PR opens and its CI run is green.

### 0.10 — Staging host + managed Postgres in an EU region; deploy the empty service

_Infra_

**What**

Staging is a copy of production used for testing: same kind of host, same kind of database, separate data. Pick the host now (Render, Fly.io, Railway, or a cloud VM) and its managed Postgres. Create both in an EU region (e.g. Frankfurt): the data is health data under GDPR, and moving a database between regions later means downtime and a migration.

**Managed Postgres**

The provider runs the database for you — upgrades, backups, failover. You get a connection string. For a small team this is far cheaper than operating Postgres yourself.

**Why deploy an empty service**

Deployment problems (networking, env vars, TLS, health checks, image pulls) are easiest to fix when there's no application logic in the way. After this step every merge to main can ship automatically, and later stages only add features.

**Done when**

https://staging-api…/actuator/health returns UP, served from the image CI built.

### 0.11 — Expo with dev client, TypeScript strict, ESLint

_App_

**What**

Create the mobile app in app/ with create-expo-app (TypeScript template).

**Dev client**

Expo Go, the generic preview app, can't load native modules you add yourself. HealthKit, Health Connect and Sign in with Apple are native modules, so you need a development build: your own debug version of the app with those modules compiled in (expo-dev-client). Set it up now so you never have to migrate later.

**TypeScript strict**

"strict": true in tsconfig.json. It catches null/undefined mistakes at compile time, which is most of the crashes in a React Native app.

**ESLint**

Lints for bugs and React hook mistakes. Add a CI workflow for app/ that runs tsc --noEmit and eslint on PRs.

**Done when**

The dev build runs on a physical iPhone and an Android phone and hot-reloads an edit.

### 0.12 — EAS build profiles: development, preview, production

_App_

**What**

EAS (Expo Application Services) builds the native iOS and Android binaries in the cloud. eas.json defines build profiles.

**Profiles**

development — the dev client from 0.11, pointed at your local or staging API.  
preview — a release-like build for testers, pointed at staging.  
production — store builds, pointed at the production API.

Each profile sets its API base URL through an environment variable so one codebase targets each backend.

**Why now**

Code signing, bundle IDs and Apple/Google developer accounts take days to sort out. Discovering that in week 10 delays the beta.

**Done when**

eas build --profile preview produces an installable build on both platforms.

### 0.13 — EAS Update channels per profile; runtimeVersion policy

_App_

**What**

EAS Update pushes new JavaScript and assets to installed apps without a store release. A channel (development, preview, production) decides which builds receive which updates.

**runtimeVersion**

An update can only replace JavaScript. If you add a native module, older binaries can't run the new code. runtimeVersion (use the "fingerprint" policy) labels which binaries an update is compatible with, so an incompatible update is never delivered to them.

**Why**

During the beta most fixes are JavaScript-only. Shipping them in minutes instead of waiting for TestFlight or Play review keeps testers from giving up on a broken build.

**Rules**

Native changes still need a new build. Publish to preview first and test before publishing to production.

**Done when**

A text change published to the preview channel appears on a preview build after a restart.

## Stage 1 — Foundations

Module: identity · versioning · crash reporting

**Milestone:** Sign in on a real iPhone and Android device against staging

- [ ] **1.01** `Database` V2: refresh_tokens (user_id, family_id, token_hash, expires_at, revoked_at)
- [ ] **1.02** `Backend` JPA entities + repositories for users, profiles, refresh_tokens
- [ ] **1.03** `Backend` Global error handler returning ProblemDetail with stable error codes
- [ ] **1.04** `Backend` All routes under /v1; breaking changes only in a new version
- [ ] **1.05** `Backend` Verify Apple and Google ID tokens against provider JWKS (aud, iss, exp, nonce)
- [ ] **1.06** `Backend` POST /auth/{provider}: upsert user, issue 15-min access JWT + refresh token
- [ ] **1.07** `Backend` POST /auth/refresh with rotation; reuse of an old token revokes the family
- [ ] **1.08** `Backend` Security filter chain: JWT resource server; all routes but /auth/** authenticated
- [ ] **1.09** `Backend` Integration tests: sign-in, refresh rotation, reuse detection, expired token
- [ ] **1.10** `Backend` GET /app-config: minimum supported app version + feature flags
- [ ] **1.11** `Backend` springdoc OpenAPI; CI fails when the committed spec is out of date
- [ ] **1.12** `App` expo-router layout and theme file from design tokens
- [ ] **1.13** `App` Generate the typed API client from the OpenAPI spec
- [ ] **1.14** `App` Sign in with Apple and Google; tokens in SecureStore
- [ ] **1.15** `App` Fetch wrapper: attach token, refresh once on 401, sign out on second failure
- [ ] **1.16** `App` Forced-update screen when the app is below the minimum version
- [ ] **1.17** `App` Crash reporting on app and backend, before any tester

### 1.01 — V2: refresh_tokens (user_id, family_id, token_hash, expires_at, revoked_at)

_Database_

**What**

A table for refresh tokens, the long-lived credentials the app trades for new access tokens (see 1.06–1.07).

**Columns**

user_id — whose token.  
family_id — all tokens descended from one sign-in share a family; used for reuse detection.  
token_hash — SHA-256 of the token. Store the hash, never the token: a leaked database then can't be used to log in as anyone.  
expires_at — e.g. 60 days.  
revoked_at — set on logout, rotation or suspected theft.

**Index**

Unique on token_hash (lookups on every refresh), plus user_id for "log out everywhere" and account deletion.

**Done when**

V2 applies on all environments.

### 1.02 — JPA entities + repositories for users, profiles, refresh_tokens

_Backend_

**What**

Java classes mapped to tables (@Entity) and Spring Data interfaces (UserRepository extends JpaRepository<User, UUID>) that give you save/find without writing SQL.

**Guidance**

Map only what the code needs right now; add entities for other tables in the stage that uses them. Use UUID ids matching the schema. Don't expose entities in API responses — map to DTO records, so the database shape and the API shape can change independently.

**Done when**

With ddl-auto=validate the app starts, and a repository test saves and reloads a user.

### 1.03 — Global error handler returning ProblemDetail with stable error codes

_Backend_

**What**

One @RestControllerAdvice class that turns every exception into a consistent JSON error body using Spring's ProblemDetail (the RFC 9457 standard): type, title, status, detail, plus your own "code" field.

**Stable error codes**

An enum like VALIDATION_FAILED, NOT_FOUND, TOKEN_EXPIRED, COST_CEILING_REACHED. The app branches on code, never on the human-readable message, so you can reword messages without breaking clients.

**Why first**

Every endpoint after this returns errors the same way, and the app needs exactly one error parser.

**Done when**

Invalid JSON, a validation failure and an unknown route all return ProblemDetail with a code. Stack traces never reach the client.

### 1.04 — All routes under /v1; breaking changes only in a new version

_Backend_

**What**

Prefix every route with /v1, e.g. one @RequestMapping("/v1") on a shared controller annotation, or spring.mvc.servlet.path. Paths elsewhere in this roadmap omit the prefix for brevity.

**Why**

A website updates for everyone at once; a mobile app doesn't. People keep old versions for months, so once an endpoint ships, its shape is a contract with every installed copy.

**Rules**

Within v1 make only additive changes: new endpoints, new optional fields, new enum values the app can tolerate. Removing or renaming a field, or changing what it means, requires /v2 running alongside /v1 until old apps are gone (enforced with the minimum version from 1.10). App code must ignore fields it doesn't know.

**Done when**

All controllers live under /v1 and these rules are written in api/README.md.

### 1.05 — Verify Apple and Google ID tokens against provider JWKS (aud, iss, exp, nonce)

_Backend_

**What**

When a user signs in with Apple or Google on the phone, the provider gives the app an ID token: a signed JWT saying "this is user X". The app sends it to your API, which must check it's genuine before trusting it.

**JWKS**

Each provider publishes its public signing keys at a URL (JSON Web Key Set). Verify the token's signature against those keys. Use Nimbus JOSE (already in Spring Security) and cache the keys; they rotate occasionally.

**Claims to check**

aud — must be your app's client ID, otherwise someone could reuse a token issued to a different app.  
iss — must be https://appleid.apple.com or https://accounts.google.com.  
exp — not expired.  
nonce — a random value the app generated before sign-in; matching it stops a captured token from being replayed.

**Done when**

Unit tests pass for valid, expired, wrong-audience and bad-signature tokens (sign test tokens with your own key pair).

### 1.06 — POST /auth/{provider}: upsert user, issue 15-min access JWT + refresh token

_Backend_

**What**

The sign-in endpoint. The app posts the verified provider ID token; you find or create the user, then issue your own two tokens.

**Upsert**

Insert the user if (provider, provider_subject) is new, otherwise load the existing one — one SQL statement with ON CONFLICT. The provider's "sub" claim is the stable ID; email can change or be hidden by Apple.

**Two tokens**

Access token — a short-lived JWT (15 minutes) signed with your key, sent on every request. Short life limits damage if stolen; the server never needs to look it up.  
Refresh token — a long random string, stored hashed (1.01), used only to get a new access token. It can be revoked, which access tokens can't.

**Done when**

Posting a valid test ID token returns both tokens, and a second call for the same user doesn't create a duplicate.

### 1.07 — POST /auth/refresh with rotation; reuse of an old token revokes the family

_Backend_

**Rotation**

Each refresh returns a new refresh token and revokes the one just used. A stolen refresh token stays useful only until the real app refreshes again.

**Reuse detection**

If a token that was already rotated is presented again, either the thief or the real user has an old copy — you can't tell which. Revoke the whole family (every token from that sign-in), forcing a fresh sign-in. That is what family_id is for.

**Logout**

POST /auth/logout revokes the current family. Access tokens still work until they expire (max 15 min), which is acceptable.

**Concurrency**

The app may fire two refreshes at once. Make the app serialize them (1.15) rather than loosening the server rule.

**Done when**

Refresh returns a new pair; reusing the old refresh token returns 401 and revokes the family.

### 1.08 — Security filter chain: JWT resource server; all routes but /auth/** authenticated

_Backend_

**What**

Spring Security's SecurityFilterChain bean decides, for every request, who the caller is and whether they're allowed in.

**Resource server**

Configure oauth2ResourceServer().jwt() with your own signing key. Spring then validates the Authorization: Bearer <access token> header on every request and exposes the user ID to controllers.

**Rules**

permitAll on /auth/** and /actuator/health; everything else authenticated. Stateless sessions (no cookies). CSRF off, because there are no cookies to forge.

**Ownership**

Authentication says who; each query must still filter by that user's ID so one user can never read another's rows. Write it into every repository method now (findByIdAndUserId).

**Done when**

A request without a token gets 401; with a valid token it reaches the controller.

### 1.09 — Integration tests: sign-in, refresh rotation, reuse detection, expired token

_Backend_

**What**

Tests through real HTTP (MockMvc or WebTestClient) against Testcontainers Postgres, covering the whole identity flow.

**Cases**

Sign-in creates a user once. Refresh rotates. Reused refresh token → 401 + family revoked. Expired access token → 401 with code TOKEN_EXPIRED. Logout revokes.

**Why**

Auth bugs are security bugs and they're invisible in normal use. These tests are cheap now and stop regressions when you touch security config later.

**Done when**

All pass in CI.

### 1.10 — GET /app-config: minimum supported app version + feature flags

_Backend_

**What**

An unauthenticated endpoint the app calls on launch, returning e.g. { minVersion: "1.2.0", latestVersion: "1.4.0", flags: { photoEstimates: true } }.

**Why**

If an old build has a bug that corrupts data, or an API change can't stay compatible, you need a way to stop old builds. Feature flags let you switch a feature off — photo estimates during a vision-provider outage or a cost spike — without a release.

**How**

Values live in a small config table (same pattern as verdict_config), cached in memory. Respond with Cache-Control: max-age=300.

**Done when**

Changing minVersion in the database changes the response within five minutes.

### 1.11 — springdoc OpenAPI; CI fails when the committed spec is out of date

_Backend_

**What**

springdoc-openapi reads your controllers and DTOs and produces an OpenAPI spec (a machine-readable description of every endpoint) at /v3/api-docs, plus Swagger UI for browsing it.

**Committed spec**

Save the spec as api/openapi.json in the repo. A test generates the current spec and fails if it differs from the committed file. API changes then show up as a visible diff in every PR, and the app generates its client from that file (1.13).

**Done when**

Changing a DTO without regenerating openapi.json fails CI.

### 1.12 — expo-router layout and theme file from design tokens

_App_

**What**

expo-router turns files in app/app/ into screens: (tabs)/today.tsx becomes the Today tab. Set up the root layout: auth gate, tab bar, stack for modals.

**Theme file**

One theme.ts holding colors, type sizes, spacing and radii copied from the design tokens. Screens reference theme.color.accent, never a hex value, so the look changes in one place.

**Done when**

Empty Today, Train, Fuel and Progress tabs navigate, styled from the theme.

### 1.13 — Generate the typed API client from the OpenAPI spec

_App_

**What**

Use openapi-typescript (types) with openapi-fetch (a tiny typed client) to turn api/openapi.json into TypeScript. Add an npm script: npm run gen:api.

**Why**

Every request and response is type-checked against the real backend. When the API changes, tsc points at every screen that breaks — instead of a crash in a tester's hands.

**Rule**

Never hand-write request or response types.

**Done when**

Calling GET /actuator/health through the generated client compiles and works.

### 1.14 — Sign in with Apple and Google; tokens in SecureStore

_App_

**What**

The sign-in screen. expo-apple-authentication for Apple (required by App Store rules if you offer Google), @react-native-google-signin for Google. Each returns a provider ID token, which you post to /auth/{provider}.

**SecureStore**

expo-secure-store saves tokens in the iOS Keychain and Android Keystore, encrypted by the OS. AsyncStorage is plain text — never put tokens there.

**Done when**

Signing in on a device stores tokens, and relaunching the app skips the sign-in screen.

### 1.15 — Fetch wrapper: attach token, refresh once on 401, sign out on second failure

_App_

**What**

One function every API call goes through, so no screen handles auth itself.

**Behaviour**

Adds the Authorization header. On 401 TOKEN_EXPIRED: refresh once, retry the original request once. If the refresh itself fails, clear tokens and return to sign-in.

If several requests hit 401 together, they wait on a single shared refresh promise. This is the client half of 1.07: without it, parallel refreshes look like token reuse and log the user out.

**Done when**

With the access token TTL set to 1 minute in staging, the app keeps working past the minute without a visible sign-in.

### 1.16 — Forced-update screen when the app is below the minimum version

_App_

**What**

On launch and on foreground, compare the installed version (expo-application) with /app-config. Below minVersion: a full-screen "Update required" with a store link, blocking the app. Below latestVersion only: a dismissible banner.

**Offline**

If /app-config can't be reached, let the app run. Never lock out someone logging a workout without signal. Unsynced outbox items stay on the phone and sync after the update.

**Done when**

Raising minVersion on staging blocks a preview build and the store link opens.

### 1.17 — Crash reporting on app and backend, before any tester

_App_

**What**

A crash reporting service (e.g. Sentry with its Expo SDK) captures JavaScript errors and native crashes with stack traces. This now sits in Foundations so it is live before the first tester installs the app.

**Detail**

Upload source maps from EAS builds so traces point at your TypeScript lines. Strip health values and personal data from reports. Add the Spring SDK on the backend too for unhandled exceptions.

**Done when**

A deliberate test crash on each platform appears with a readable stack trace.

## Stage 2 — Logging core

Module: privacy · program · training · analytics

**Milestone:** First real lifters: do people log at all?

- [ ] **2.01** `Infra` Privacy policy, processing record, DPA with every provider
- [ ] **2.02** `Database` V3: exercise catalogue seed
- [ ] **2.03** `Backend` GET/PUT /me/profile with validation (units, IANA timezone)
- [ ] **2.04** `Backend` Program CRUD: programs, days, exercises; activating one deactivates others in one transaction
- [ ] **2.05** `Backend` GET /exercises with search; user custom exercises
- [ ] **2.06** `Backend` POST /sessions and /sessions/{id}/sets: idempotent on client_uuid (ON CONFLICT DO NOTHING, return existing)
- [ ] **2.07** `Backend` POST /food, PUT /sleep/{night} (manual), PUT /bodyweight/{date}: same idempotency
- [ ] **2.08** `Database` V4: version, updated_at, deleted_at on editable tables
- [ ] **2.09** `Backend` PATCH and DELETE for sessions, sets, food: version check, soft delete
- [ ] **2.10** `Backend` GET /days/{date}: plan as written + logged data, resolved in the user timezone
- [ ] **2.11** `Backend` Tests: retries return the same row; other users' ids return 404
- [ ] **2.12** `Database` Seed 10k rows; EXPLAIN ANALYZE every query in test_queries.sql; V5 for missing indexes
- [ ] **2.13** `App` SQLite schema mirroring writes + outbox table
- [ ] **2.14** `App` Outbox worker: FIFO flush on connectivity, exponential backoff, client_uuid per row
- [ ] **2.15** `App` Jest tests for the outbox: order, backoff, 4xx handling, crash mid-flush
- [ ] **2.16** `App` Health-data consent in onboarding; withdraw in settings
- [ ] **2.17** `App` Onboarding writing PUT /me/profile
- [ ] **2.18** `App` Program builder
- [ ] **2.19** `App` Train screen writing through the outbox
- [ ] **2.20** `App` Manual sleep and food entry
- [ ] **2.21** `App` Maestro end-to-end flows: sign in, onboarding, offline workout
- [ ] **2.22** `App` Product analytics events (EU-hosted, opt-in, no health values)

### 2.01 — Privacy policy, processing record, DPA with every provider

_Infra_

**What**

The legal groundwork for processing health data, finished before the first tester signs in.

**Why**

Sleep, bodyweight, food and training data are health data — a "special category" under GDPR Article 9. Processing it needs explicit consent, a stated purpose, and control over who receives it. Both app stores also reject health apps without a privacy policy.

**Privacy policy**

What you collect, why, how long you keep it, which companies process it (host, database, vision provider, crash reporting, analytics), and how users delete it. Publish it on the landing site and link it from the app and store listings.

**Processing record**

A short internal document (GDPR Article 30) listing each data type, its purpose, where it is stored and how long it is kept.

**DPA**

A data processing agreement with each service that touches user data; most providers let you accept one online. For the vision provider, confirm in writing that it doesn't retain uploaded images or use them for training.

**Region**

Keep the database, API host, analytics and crash reporting in the EU, as chosen in 0.10.

**Done when**

The policy is published and every provider on the list has a DPA in place.

### 2.02 — V3: exercise catalogue seed

_Database_

**What**

A migration that inserts the built-in exercise list (bench press, squat, deadlift…) with their muscle groups and equipment.

**Why a migration**

Every environment gets the identical catalogue with fixed UUIDs, so programs and tests can reference exercises reliably. Later changes to the list are new migrations.

**Note**

The schema has a unique index on lower(name) for non-custom exercises, so a duplicate name fails the migration — that's intended.

**Done when**

GET from a test returns the full catalogue.

### 2.03 — GET/PUT /me/profile with validation (units, IANA timezone)

_Backend_

**What**

Read and update the signed-in user's profile: sex, birth year, height, units (kg/lb), goal, timezone.

**Validation**

Bean Validation annotations on the request DTO (@Min, @Max, @Pattern). Timezone must be a valid IANA name like Europe/Tirane — check with ZoneId.of() — because every "day" in the app is computed in it.

**PUT semantics**

PUT replaces the whole profile; the app sends all fields. Simpler than partial PATCH and fine for a small object.

**Done when**

Valid PUT saves; invalid timezone returns 400 VALIDATION_FAILED.

### 2.04 — Program CRUD: programs, days, exercises; activating one deactivates others in one transaction

_Backend_

**What**

Create, read, update and delete a training program with its days and the exercises on each day (sets, rep range, order).

**One transaction**

A user has exactly one active program (enforced by the partial unique index programs_one_active). Activating program B means: set A inactive, set B active. Do both inside one @Transactional method, deactivate first — otherwise the index rejects the second update or the user is briefly left with none.

**Shape**

Save the whole program tree in one request (program → days → exercises). The builder edits locally and saves once, which is simpler than many small endpoints.

**Done when**

Tests cover create, edit, reorder and switching active programs.

### 2.05 — GET /exercises with search; user custom exercises

_Backend_

**What**

List and search exercises for the program builder, plus let a user create their own (custom = true, user_id set).

**Search**

WHERE lower(name) LIKE lower(:q) || '%' is enough at this size. Return catalogue + the caller's own custom exercises, never other users'.

**Done when**

Searching "ben" returns Bench press; a custom exercise is visible only to its creator.

### 2.06 — POST /sessions and /sessions/{id}/sets: idempotent on client_uuid (ON CONFLICT DO NOTHING, return existing)

_Backend_

**What**

Start a session and log sets (exercise, weight, reps, RIR).

**Idempotent**

The phone may send the same write twice — the request succeeded but the response was lost on a bad connection, so the outbox retries. Idempotent means repeating it has no extra effect.

The app generates a client_uuid for each write. The server inserts with INSERT … ON CONFLICT (client_uuid) DO NOTHING, then selects the row by client_uuid and returns it. First call creates; retries return the same row. No duplicate sets, no errors.

**Done when**

Posting the same set twice yields one row and two identical 200 responses.

### 2.07 — POST /food, PUT /sleep/{night} (manual), PUT /bodyweight/{date}: same idempotency

_Backend_

**What**

The same idempotent write pattern as 2.06 for the other things a user logs by hand: meals, a night of sleep, morning bodyweight.

**Keys**

Food uses client_uuid (many per day). Sleep is keyed by night and source, bodyweight by date — a second PUT for the same key overwrites, which is idempotent by nature.

**Done when**

All three accept retries safely and appear in the database.

### 2.08 — V4: version, updated_at, deleted_at on editable tables

_Database_

**What**

Columns that let rows be edited and deleted safely from offline queues: version (integer, starts at 1, +1 on every change), updated_at, and deleted_at (null unless deleted).

**Where**

sessions, set_logs, food_logs, saved_meals, bodyweight. Add only what is missing; food_logs already has deleted_at.

**Why soft delete**

The phone and the verdict both need to learn that a row went away. A hard DELETE leaves nothing to sync; a deleted_at timestamp keeps the fact until account deletion removes the row for real.

**Indexes**

Recreate the per-day indexes with WHERE deleted_at IS NULL so reads stay fast and skip deleted rows.

**Done when**

V4 applies and the JPA entities map version with @Version.

### 2.09 — PATCH and DELETE for sessions, sets, food: version check, soft delete

_Backend_

**What**

Endpoints to correct a set (wrong weight), change a meal, or delete either.

**Version check**

The app sends the version it last saw. The server runs UPDATE … WHERE id = :id AND user_id = :uid AND version = :v. One row updated: success, return the new version. Zero rows: someone changed it first; return 409 with the current row.

**Conflict rule**

A personal log is edited almost always from one phone, so last write wins: on 409 the app takes the server row, re-applies the user's change and retries once. The version check exists so a stale queued edit can't silently overwrite a newer one.

**Delete**

DELETE sets deleted_at and bumps version. Deleting an already-deleted row returns 204, so retries are safe. All reads exclude deleted rows.

**Recalculate**

Edits that change today's inputs call recalculate (3.07); it does nothing when the hash is unchanged.

**Done when**

Tests cover edit, stale edit (409), delete and double delete.

### 2.10 — GET /days/{date}: plan as written + logged data, resolved in the user timezone

_Backend_

**What**

The main read the Today and Train screens need: for a given date, the planned workout from the active program, plus what was actually logged (sets, food, sleep, bodyweight).

**Timezone**

{date} is the user's local date. A set logged at 00:30 in Tirana belongs to that date even though it's still the previous day in UTC. Store local_date on each row when it's written (computed from the profile timezone), and query by it.

**Done when**

A test with a session at 23:50 and one at 00:10 local time puts them on the right days.

### 2.11 — Tests: retries return the same row; other users' ids return 404

_Backend_

**What**

Integration tests for the risks in this stage.

**Cases**

Idempotency: the same client_uuid twice → one row, same response.  
Isolation: user B requesting user A's session, program or set ID gets 404 — not 403, which would confirm the ID exists.

**Why**

Duplicate data and cross-user leaks are the two failures that cost user trust permanently.

**Done when**

Green in CI.

### 2.12 — Seed 10k rows; EXPLAIN ANALYZE every query in test_queries.sql; V5 for missing indexes

_Database_

**What**

Generate realistic volume (a script that creates ~10,000 sets, meals and nights for a handful of users) and check each query in test_queries.sql with EXPLAIN ANALYZE.

**EXPLAIN ANALYZE**

Postgres runs the query and shows how: which indexes it used, where it read the whole table (Seq Scan), how long each step took. A Seq Scan on a big table for a per-user query means a missing index.

**Fix**

Add missing indexes in a V5 migration. Don't add indexes speculatively — each one slows writes.

**Done when**

Every screen query runs under ~10 ms on the seeded data.

### 2.13 — SQLite schema mirroring writes + outbox table

_App_

**What**

A local SQLite database on the phone (expo-sqlite) with tables mirroring what the user writes — sessions, sets, food, sleep, bodyweight — plus an outbox table.

**Outbox**

Every write goes into the local table and into the outbox (method, path, body, client_uuid, attempts) in one local transaction. The UI reads from local tables, so logging is instant and works with no signal. A worker sends the outbox to the server later (2.14).

**Migrations**

Version the local schema too (a user_version pragma and numbered steps), since app updates must upgrade existing phones.

**Done when**

A set logged in airplane mode shows on screen and sits in the outbox.

### 2.14 — Outbox worker: FIFO flush on connectivity, exponential backoff, client_uuid per row

_App_

**What**

The background process that drains the outbox to the API.

**Rules**

FIFO — send in the order written, because a set depends on its session existing first.  
On connectivity — trigger on network regained (NetInfo), app foreground, and after each new write.  
Exponential backoff — on failure wait 1s, 2s, 4s… up to a few minutes, so a down server isn't hammered.  
On 4xx (except 401/429) — the request is wrong and retrying won't help; mark it failed and surface it, don't block the queue forever.  
Remove an item only after a 2xx.

**Done when**

Ten sets logged offline all reach the server, once each, in order, after reconnecting.

### 2.15 — Jest tests for the outbox: order, backoff, 4xx handling, crash mid-flush

_App_

**What**

Jest unit tests for the outbox and sync code, run by the app CI workflow.

**Cases**

Items send in FIFO order. Failures follow the backoff timings. A 4xx marks the item failed without blocking the queue. A 401 triggers exactly one refresh. The app is killed mid-flush: on restart nothing is lost, and anything resent is harmless because of client_uuid.

**How**

Put the worker behind small interfaces — storage, network, clock — and test with fakes. Tests then run in milliseconds without a device or real waiting.

**Why**

This is the code that loses someone's workout if it's wrong, and its bugs only appear on bad connections that are hard to reproduce by hand.

**Done when**

All cases pass in CI.

### 2.16 — Health-data consent in onboarding; withdraw in settings

_App_

**What**

A screen in onboarding, before any health data is logged or read, saying what Calozone stores and why, with an explicit agree action. Not pre-ticked and not bundled with accepting the terms.

**Record it**

Store consented_at and the policy version on the server (a small migration adding them to profiles). If the policy changes materially, ask again.

**Withdraw**

A settings option to withdraw consent. Since the app can't work without health data, withdrawing leads to account deletion (7.05), with a clear warning first.

**Analytics**

Analytics consent is a separate, optional switch (see the analytics step at the end of this stage).

**Done when**

A new user can't reach Today without consenting, and the consent timestamp is stored.

### 2.17 — Onboarding writing PUT /me/profile

_App_

**What**

The onboarding screens from the design, ending in one PUT /me/profile.

**Detail**

Read the device timezone (Intl.DateTimeFormat().resolvedOptions().timeZone) and send it; don't ask the user. Units default from locale.

**Done when**

A new user completes onboarding and the profile row matches their answers.

### 2.18 — Program builder

_App_

**What**

Create and edit a program: days, exercises from search (2.05), sets and rep ranges, reorder.

**Detail**

Edit in local state, save the whole tree once (2.04). Warn before leaving with unsaved changes.

**Done when**

A program built on the phone appears in GET /days as the plan.

### 2.19 — Train screen writing through the outbox

_App_

**What**

The Train screen: shows today's planned exercises, logs each set.

**Detail**

Each set is written to SQLite + outbox (2.13) and appears instantly; the network is never in the path of tapping "Log". Pre-fill weight and reps from the last session of that exercise.

**Done when**

A full workout can be logged with the phone in airplane mode and syncs afterwards.

### 2.20 — Manual sleep and food entry

_App_

**What**

Simple forms for food (name, kcal, protein, carbs, fat) and sleep (bed and wake time), both through the outbox.

**Why now**

Manual entry is the fallback for later automatic paths (Health sync in stage 4, photos in stage 5). It also lets the first testers produce the data the verdict needs.

**Done when**

Testers can log a full day — training, food, sleep — by hand.

### 2.21 — Maestro end-to-end flows: sign in, onboarding, offline workout

_App_

**What**

Maestro runs scripted flows written in YAML on a simulator or device: tap, type, toggle network, assert text. The closest thing to a tester following a checklist.

**Flows**

Sign in. Complete onboarding. Switch on airplane mode, log a workout, switch it off, and assert the sets reached the server.

**Test login**

Apple and Google sign-in can't be automated. Add a test-only login enabled solely under the staging profile, with a config check that fails startup if it is ever enabled in prod.

**When**

Run the flows on each preview build before it goes to testers. Add them to CI later (Maestro Cloud or an emulator runner).

**Done when**

The three flows pass on iOS and Android.

### 2.22 — Product analytics events (EU-hosted, opt-in, no health values)

_App_

**What**

Count what users do, so the milestone question — do people log at all? — gets a real answer.

**Events**

signed_up, onboarding_completed, set_logged, session_completed, food_logged (with source: manual, photo, saved, repeat), sleep_synced, verdict_viewed, verdict_applied, verdict_declined. Properties are counts and enums only.

**No health values**

Never send weights, kcal, sleep hours or food names. Identify users by a random analytics ID, not email.

**Tool**

PostHog on its EU cloud, or similar: funnels and retention charts without building dashboards. Make it opt-in in settings. For testers who decline, the Postgres tables still give aggregate counts.

**Done when**

A retention chart shows week-one logging for the first testers.

# Continue

_Stages 3–6 · what makes it more than a logbook_

## Stage 3 — The verdict

Module: verdict

- [ ] **3.01** `Database` V6: verdict_config (key, value, updated_at) seeded with thresholds
- [ ] **3.02** `Backend` VerdictInputs record + pure VerdictRules.evaluate(inputs, config): no Spring, no clock
- [ ] **3.03** `Backend` Table-driven JUnit cases for every rule, including the three-night gate
- [ ] **3.04** `Backend` InputsAssembler: loads sleep, sets, food, bodyweight for a local date
- [ ] **3.05** `Backend` inputs_hash = SHA-256 of canonical JSON of the inputs
- [ ] **3.06** `Backend` issue(): insert day_verdicts row with reasoning jsonb; live unique index guards races
- [ ] **3.07** `Backend` recalculate(): if hash changed, insert new row + set superseded_by in one transaction
- [ ] **3.08** `Backend` POST /verdicts/{id}/apply and /decline with guarded state transitions
- [ ] **3.09** `Backend` GET /days/{date} includes the live verdict; ETag from verdict id
- [ ] **3.10** `Backend` Tests: concurrent issue yields one live row; a sync never recalculates
- [ ] **3.11** `App` Cache the verdict in SQLite; show cached when offline
- [ ] **3.12** `App` Today wired: reasoning rows, apply, decline
- [ ] **3.13** `App` Changed-numbers and paused states

### 3.01 — V6: verdict_config (key, value, updated_at) seeded with thresholds

_Database_

**What**

A small key-value table holding the verdict thresholds (e.g. sleep_short_hours = 6.0, nights_required = 3, kcal_deficit_cut = 300).

**Why a table**

These numbers are guesses that will be tuned during the beta. In a table you change them with one UPDATE, no app release, no redeploy. Load them into memory and refresh every few minutes.

**Done when**

V6 applies with seeded defaults, and a config service returns them typed.

### 3.02 — VerdictInputs record + pure VerdictRules.evaluate(inputs, config): no Spring, no clock

_Backend_

**What**

The heart of the product: a function that takes all the facts about a day and returns the verdict (set change, kcal change, reasons).

**Pure**

VerdictInputs is a Java record with everything the rules need. VerdictRules.evaluate(inputs, config) uses nothing else: no database, no Spring beans, no LocalDate.now(). Same input → same output, always.

**Why**

Pure code is trivial to test exhaustively (3.03), easy to reason about, and makes inputs_hash (3.05) meaningful. All the messy loading lives elsewhere (3.04).

**Done when**

The class compiles with no Spring imports.

### 3.03 — Table-driven JUnit cases for every rule, including the three-night gate

_Backend_

**What**

Table-driven tests: one test method fed by a list of rows (inputs → expected verdict) using JUnit @ParameterizedTest with @MethodSource.

**Cover**

Each rule alone, rules combining, boundary values (exactly 6.0 h sleep vs 5.9), missing data. The three-night gate: the verdict shouldn't react to sleep until at least three nights exist; test 0, 2, 3 and 4 nights.

**Why**

Adding a case is one line, so you'll actually add them. When a tester reports a strange verdict, reproduce it as a row first, then fix.

**Done when**

Every branch in VerdictRules is covered.

### 3.04 — InputsAssembler: loads sleep, sets, food, bodyweight for a local date

_Backend_

**What**

The impure side: a Spring service that, for a user and local date, queries sleep nights, recent sets, food totals, bodyweight trend and the plan, and builds a VerdictInputs.

**Detail**

Keep it read-only and do it in a few aggregate queries, not one per row. Its tests use Testcontainers with fixture data.

**Done when**

For a seeded user, the assembled inputs match hand-computed values.

### 3.05 — inputs_hash = SHA-256 of canonical JSON of the inputs

_Backend_

**What**

A fingerprint of the inputs: serialize VerdictInputs to JSON with keys in a fixed order and numbers in a fixed format (canonical JSON), then SHA-256 it.

**Why**

Store the hash with each verdict. When something might have changed, recompute the hash: same hash = nothing that matters changed, so no new verdict. This is what keeps the verdict frozen during the day unless real inputs change.

**Canonical**

Without fixed ordering and formatting, identical data can serialize differently and the hash changes for nothing. Configure Jackson with ORDER_MAP_ENTRIES_BY_KEYS and sorted properties.

**Done when**

Same inputs always give the same hash; changing any field changes it.

### 3.06 — issue(): insert day_verdicts row with reasoning jsonb; live unique index guards races

_Backend_

**What**

Create today's verdict: assemble inputs, evaluate, insert a day_verdicts row with set_delta, kcal_delta, reasoning (jsonb), inputs_hash, state = issued.

**jsonb**

reasoning is a list of reason objects whose shape will evolve. jsonb stores it as structured JSON in Postgres without a schema change each time.

**Races**

Two requests may try to issue at the same moment. The partial unique index day_verdicts_live (one row per user per day where superseded_by IS NULL) makes the second insert fail; catch that and return the existing row.

**Done when**

Issuing twice returns the same verdict.

### 3.07 — recalculate(): if hash changed, insert new row + set superseded_by in one transaction

_Backend_

**What**

Re-check a verdict after inputs may have changed (e.g. last night's sleep arrived).

**Append-only**

Never update a verdict's numbers. If the new hash differs: insert the new verdict and set the old one's superseded_by to point at it, in one transaction. If the hash is the same, do nothing. You keep a full history of what the user was told and why.

**Order**

Within the transaction, set superseded_by on the old row first so the live unique index allows the new one.

**Done when**

Changing sleep creates a second row and supersedes the first; no change creates nothing.

### 3.08 — POST /verdicts/{id}/apply and /decline with guarded state transitions

_Backend_

**What**

The user acts on the verdict: apply (use the adjusted numbers today) or decline (keep the plan as written).

**State machine**

Allowed: issued → applied, issued → declined. Everything else (applying a superseded verdict, declining twice) returns 409 Conflict with a code. Implement it as one guarded UPDATE: … SET state = 'applied' WHERE id = :id AND state = 'issued'; zero rows updated means the move wasn't allowed.

**Done when**

Tests cover every allowed and disallowed transition.

### 3.09 — GET /days/{date} includes the live verdict; ETag from verdict id

_Backend_

**What**

Add the live verdict to the day response so Today needs one request.

**ETag**

An HTTP header identifying a response version — here, the verdict ID. The app sends it back as If-None-Match; if the verdict hasn't changed the server answers 304 Not Modified with no body. Cheap polling, and the app knows exactly when numbers changed.

**Done when**

A second fetch with the ETag returns 304 until a recalculation happens.

### 3.10 — Tests: concurrent issue yields one live row; a sync never recalculates

_Backend_

**Concurrency test**

Fire 10 parallel issue() calls for the same user and day (ExecutorService + CountDownLatch); assert exactly one live row.

**Sync test**

Replaying already-synced writes (same client_uuids) must not create a new verdict. Recalculation runs only when inputs actually change, never as a side effect of retries — otherwise flaky connections would make the numbers jump.

**Done when**

Both pass reliably in CI (run them 20 times locally once).

### 3.11 — Cache the verdict in SQLite; show cached when offline

_App_

**What**

Store the latest verdict for each day in SQLite with its ETag.

**Why**

Gyms have bad signal. Today must open instantly with the last known verdict, then quietly refresh. Show a small "updated hh:mm" when offline instead of a spinner.

**Done when**

Today renders the verdict in airplane mode after one online fetch.

### 3.12 — Today wired: reasoning rows, apply, decline

_App_

**What**

Replace placeholder data on Today with the real verdict: the numbers, the reasoning rows (from the jsonb reasons), apply and decline buttons.

**Offline**

Apply/decline go through the outbox like every write; update the local state immediately.

**Done when**

Apply on the phone shows state = applied on the server after sync.

### 3.13 — Changed-numbers and paused states

_App_

**Changed numbers**

When the ETag changes and the new verdict differs from what the user last saw, show what changed (e.g. "Sets 4 → 3 after last night's sleep") rather than silently swapping numbers.

**Paused**

When the gate blocks a verdict (fewer than three nights) or data is missing, Today explains what's needed instead of showing an empty card.

**Done when**

Both states are reachable with seeded data and match the design.

## Stage 4 — Sleep sync

Module: sleep

- [ ] **4.01** `Database` V7: unique (user_id, night_of, source) on sleep_nights; intervals jsonb
- [ ] **4.02** `Backend` PUT /sleep/{night} upsert per source; health source wins over manual
- [ ] **4.03** `Backend` Tests: duplicate nights, nights across midnight and DST changes
- [ ] **4.04** `App` SleepSource interface
- [ ] **4.05** `App` HealthKit implementation
- [ ] **4.06** `App` Health Connect implementation
- [ ] **4.07** `App` Read the last 7 nights on first open of the day; push through the outbox
- [ ] **4.08** `App` Permission denied, revoked and empty-night states

### 4.01 — V7: unique (user_id, night_of, source) on sleep_nights; intervals jsonb

_Database_

**What**

A migration ensuring each night is stored once per source: unique (user_id, night_of, source), where source is manual, healthkit or health_connect. intervals holds the raw sleep stages as jsonb.

**Why jsonb**

Each platform reports stages differently (in bed, asleep, deep, REM…). Keep the raw intervals for later analysis; extract total and efficiency into normal columns for queries.

**Done when**

V7 applies; inserting the same night and source twice fails.

### 4.02 — PUT /sleep/{night} upsert per source; health source wins over manual

_Backend_

**What**

Upsert a night: INSERT … ON CONFLICT (user_id, night_of, source) DO UPDATE. Health apps may revise a night after the fact, so a later write replaces the earlier one.

**Merge rule**

When both a manual and a health-app record exist for one night, the verdict reads the health-app one. Implement as a query preference, not by deleting the manual row.

**Recalculate**

If the night affects today's verdict, call recalculate (3.07) after saving. It no-ops if the hash didn't change.

**Done when**

Writing a health night after a manual one changes which one the verdict uses.

### 4.03 — Tests: duplicate nights, nights across midnight and DST changes

_Backend_

**What**

Edge-case tests for the sleep logic.

**Cases**

The same night sent twice. A night from 23:30 to 07:00 (belongs to the night_of the evening it started). Daylight-saving nights in March and October (23 h and 25 h). A user who changes timezone.

**Done when**

All pass; these are the bugs that otherwise show up as "my sleep is on the wrong day".

### 4.04 — SleepSource interface

_App_

**What**

A TypeScript interface the rest of the app uses: requestPermission(), getStatus(), readNights(from, to). Two implementations follow (4.05, 4.06), chosen by Platform.OS.

**Why**

Screens and sync logic never touch platform APIs directly, so they're written once and can be tested with a fake implementation.

**Done when**

A fake SleepSource drives the sync code in a unit test.

### 4.05 — HealthKit implementation

_App_

**What**

iOS implementation reading sleep analysis samples from Apple HealthKit (via a maintained library such as react-native-health or @kingstinct/react-native-healthkit).

**Setup**

HealthKit capability in the app config, NSHealthShareUsageDescription text explaining why you read sleep. Apple reviews this wording.

**Gotcha**

iOS never tells you whether the user denied read access; denied looks the same as "no data". Design for that (4.08).

**Done when**

Nights recorded by Apple Watch or another app appear in readNights().

### 4.06 — Health Connect implementation

_App_

**What**

Android implementation using Health Connect (react-native-health-connect), Google's shared health data store.

**Setup**

Declare the READ_SLEEP permission and a privacy-policy activity in the manifest; Play Console requires a health-data declaration.

**Gotcha**

Health Connect may not be installed on older Android versions; detect it and offer the Play Store link. Some phones' wearables never write sleep there at all.

**Done when**

Nights from a wearable app appear in readNights() on a test device.

### 4.07 — Read the last 7 nights on first open of the day; push through the outbox

_App_

**What**

On the first open of each day, read the last 7 nights and write each through the outbox as PUT /sleep/{night}.

**Why 7**

Covers days the app wasn't opened and revisions the health app made. Re-sending unchanged nights is harmless because the write is idempotent.

**Done when**

Opening the app in the morning makes last night appear on the server within seconds.

### 4.08 — Permission denied, revoked and empty-night states

_App_

**What**

UI for each way sleep data can be missing.

**States**

Never asked — explain the benefit, then request.  
Denied or revoked — explain where to turn it on (Settings → Health), offer manual entry.  
Connected but empty — normal for some devices; offer manual entry without alarm.

Permissions can be revoked at any time in system settings with no notification to the app, so check status on each foreground.

**Done when**

Each state is reachable on a device and none of them blocks the rest of the app.

## Stage 5 — Fuel & photos

Module: fuel

- [ ] **5.01** `Database` V8: vision_spend (user_id, local_date, calls, cost_cents)
- [ ] **5.02** `Backend` VisionClient interface + provider impl on WebClient: 10s timeout, 1 retry
- [ ] **5.03** `Backend` POST /food/estimate multipart, 2 MB max; bytes never written to disk or logs
- [ ] **5.04** `Backend` Check + increment cost ceiling in one transaction before the call; 429 when over
- [ ] **5.05** `Backend` Estimate response is editable and writes nothing to food_logs
- [ ] **5.06** `Backend` Saved meals CRUD; POST /food/repeat?from={date}
- [ ] **5.07** `Backend` Tests with VisionClient stubbed by WireMock
- [ ] **5.08** `App` Camera; resize to 1024px JPEG before upload
- [ ] **5.09** `App` Mandatory confirm screen; confirmed entry goes through the outbox
- [ ] **5.10** `App` Thumbnail saved in app storage only
- [ ] **5.11** `App` Saved meals and Repeat yesterday

### 5.01 — V8: vision_spend (user_id, local_date, calls, cost_cents)

_Database_

**What**

A table counting photo estimates per user per day: calls and cost in cents.

**Why**

Every photo estimate is a paid API call. This ledger backs the daily ceiling (5.04) and shows what the feature actually costs during the beta.

**Done when**

V8 applies with a primary key on (user_id, local_date).

### 5.02 — VisionClient interface + provider impl on WebClient: 10s timeout, 1 retry

_Backend_

**What**

An interface VisionClient { Estimate estimate(byte[] image) } and one implementation calling your chosen vision model's API with Spring WebClient.

**Timeout and retry**

Set a 10-second timeout; vision APIs sometimes hang and the user is waiting on the camera screen. Retry once on network errors or 5xx, never on 4xx.

**Why an interface**

Tests use a stub (5.07), and you can swap providers or models without touching the endpoint.

**Done when**

A manual call with a sample photo returns a parsed estimate.

### 5.03 — POST /food/estimate multipart, 2 MB max; bytes never written to disk or logs

_Backend_

**What**

The endpoint the camera screen calls: multipart/form-data with the image file.

**In memory only**

Read the upload into a byte array, pass it to VisionClient, drop it. Configure spring.servlet.multipart.max-file-size=2MB and a file-size-threshold high enough that Spring never writes the upload to a temp file. Never log the body. This is why no photo storage is needed.

**Done when**

An upload returns an estimate; nothing appears on disk or in logs; a 3 MB file returns 413.

### 5.04 — Check + increment cost ceiling in one transaction before the call; 429 when over

_Backend_

**What**

Before calling the vision API, check the user hasn't passed today's limit (e.g. 20 estimates), and count the call.

**One transaction**

UPDATE vision_spend SET calls = calls + 1 WHERE user_id = … AND local_date = … AND calls < :limit, inserting the row if missing. One row updated = allowed; zero = over the limit, return 429 COST_CEILING_REACHED. Doing check and increment together means two parallel uploads can't both slip past the limit.

**Done when**

The 21st call of the day returns 429, including when calls run in parallel.

### 5.05 — Estimate response is editable and writes nothing to food_logs

_Backend_

**What**

The estimate response is a suggestion: dish name, kcal, protein, carbs, fat, with a confidence. The endpoint writes nothing to food_logs.

**Why**

The model will be wrong sometimes. The user corrects it on the confirm screen (5.09) and only the confirmed values are saved, through the normal food write with source = 'photo'.

**Done when**

Calling estimate never changes food_logs.

### 5.06 — Saved meals CRUD; POST /food/repeat?from={date}

_Backend_

**Saved meals**

A user saves a meal they eat often with its macros and logs it again in one tap. Standard CRUD scoped to the user.

**Repeat**

POST /food/repeat?from=2026-10-26 copies that day's food entries to today with new IDs and source = 'repeat'. Require a client_uuid for the operation so a retry doesn't copy twice.

**Done when**

Both work offline-first through the app's outbox.

### 5.07 — Tests with VisionClient stubbed by WireMock

_Backend_

**What**

WireMock runs a fake HTTP server in tests that pretends to be the vision API, with canned responses.

**Cases**

Normal response, malformed response, timeout, 500 then success (retry), and the ceiling. No real API calls in CI: they cost money and fail randomly.

**Done when**

The fuel module has full coverage without network access.

### 5.08 — Camera; resize to 1024px JPEG before upload

_App_

**What**

The camera screen (expo-camera), then resize and compress the photo on the phone (expo-image-manipulator) to about 1024 px on the long edge, JPEG quality ~0.7.

**Why**

A raw phone photo is 3–8 MB; resized it's ~150 KB. Uploads are faster on gym Wi-Fi, stay under the 2 MB limit, and vision models don't need more resolution to recognise food.

**Done when**

Uploaded images are consistently under 300 KB.

### 5.09 — Mandatory confirm screen; confirmed entry goes through the outbox

_App_

**What**

After an estimate, always show an editable confirm screen. Nothing is logged until the user taps Confirm.

**Write**

The confirmed entry goes into SQLite + outbox as a normal POST /food with source = 'photo'. If the estimate request failed or the phone is offline, fall back to manual entry with the photo as reference.

**Done when**

No path logs food without confirmation.

### 5.10 — Thumbnail saved in app storage only

_App_

**What**

Keep a small thumbnail (~200 px) in the app's private storage (expo-file-system documentDirectory), referenced from the local food row.

**Why**

The food log looks better with pictures, and the server never needs them. Thumbnails leave with the app on uninstall and with account deletion.

**Done when**

Photo-logged meals show their thumbnail in the day list.

### 5.11 — Saved meals and Repeat yesterday

_App_

**What**

The saved-meals picker and the Repeat yesterday action from the design, calling 5.06.

**Done when**

Logging a usual breakfast takes one tap.

## Stage 6 — Progress

Module: training · verdict

**Milestone:** Start store paperwork: two reviews, two health-data disclosures

- [ ] **6.01** `Database` Aggregate queries (weekly volume, e1RM, bodyweight trend) with window functions; EXPLAIN on a year of data
- [ ] **6.02** `Backend` GET /progress?range=
- [ ] **6.03** `Backend` Deload detection as a pure function + tests
- [ ] **6.04** `Backend` Weekly verdict job: @Scheduled + ShedLock, per user timezone, idempotent per week
- [ ] **6.05** `App` Progress tab and charts
- [ ] **6.06** `App` Deload prompt
- [ ] **6.07** `App` Empty states for early weeks

### 6.01 — Aggregate queries (weekly volume, e1RM, bodyweight trend) with window functions; EXPLAIN on a year of data

_Database_

**What**

SQL for the Progress charts: weekly training volume per muscle group, estimated one-rep max (e1RM) per lift over time, and a smoothed bodyweight trend.

**Terms**

e1RM — an estimate of the heaviest single rep from a set, e.g. Epley: weight × (1 + reps / 30).  
Window functions — SQL that computes across neighbouring rows, like a 7-day moving average: AVG(kg) OVER (ORDER BY local_date ROWS 6 PRECEDING).

**Why in SQL**

Postgres aggregates a year of sets in milliseconds; pulling rows into Java to compute the same thing is slower and more code.

**Done when**

Each query runs under ~50 ms on a seeded year of data (check with EXPLAIN ANALYZE).

### 6.02 — GET /progress?range=

_Backend_

**What**

One endpoint returning the chart series for a range (4w, 12w, 1y).

**Detail**

Return chart-ready arrays (date, value) so the app doesn't reshape data. Add an ETag like 3.09 so revisiting the tab is cheap.

**Done when**

The response drives all Progress charts.

### 6.03 — Deload detection as a pure function + tests

_Backend_

**What**

Detect when the user likely needs a lighter week: e.g. e1RM flat or falling across several sessions combined with poor sleep or rising fatigue.

**Pure again**

Same pattern as the verdict: a pure function over prepared inputs, thresholds from verdict_config, table-driven tests.

**Done when**

Tests cover the trigger, the near-misses and the "not enough data" case.

### 6.04 — Weekly verdict job: @Scheduled + ShedLock, per user timezone, idempotent per week

_Backend_

**What**

A job that issues a weekly verdict for each user at the start of their week in their own timezone.

**ShedLock**

@Scheduled runs on every server instance. With two instances the job runs twice. ShedLock takes a lock row in Postgres so only one instance runs it.

**Timezones**

Run hourly; each run handles users whose local time just passed Monday 04:00.

**Idempotent**

A unique key on (user_id, week_start) so a rerun after a crash doesn't double-issue.

**Done when**

Running the job twice in a row produces one weekly verdict per user.

### 6.05 — Progress tab and charts

_App_

**What**

The Progress tab and its charts from the design, fed by 6.02. Use a lightweight chart library (e.g. victory-native or react-native-gifted-charts) styled from the theme.

**Done when**

Charts render on both platforms and switch ranges smoothly.

### 6.06 — Deload prompt

_App_

**What**

When 6.03 fires, show the deload suggestion with its reasons and accept/skip. Accepting adjusts the coming week's plan.

**Done when**

Seeded deload data shows the prompt; the choice is stored.

### 6.07 — Empty states for early weeks

_App_

**What**

What Progress shows before there's enough data: a new user has nothing to chart.

**Detail**

Say what will appear and when ("Your strength trend appears after 3 sessions of a lift") instead of empty axes.

**Done when**

A brand-new account sees useful empty states on every chart.

# Wrap up

_Stage 7 · harden and ship_

## Stage 7 — Hardening & beta

Module: all

**Milestone:** Closed beta on TestFlight and Play internal testing

- [ ] **7.01** `Backend` POST /sync batch: per-item result, client-timestamp order, idempotent
- [ ] **7.02** `App` Outbox switches to /sync batches; one-day airplane-mode soak test
- [ ] **7.03** `Backend` Bucket4j rate limits: per user, and per IP on /auth
- [ ] **7.04** `Backend` Micrometer + Prometheus; JSON logs with request id and no PII
- [ ] **7.05** `Backend` DELETE /me: cascade delete + revoke tokens; test asserts zero rows remain
- [ ] **7.06** `Database` Prod Postgres: daily backups + PITR; restore drill into staging
- [ ] **7.07** `Infra` Prod deploy; Flyway migrates on startup; rollback = forward-fix migration
- [ ] **7.08** `App` Notifications and timezone-change handling
- [ ] **7.09** `App` Store listings, privacy labels, health-data disclosures
- [ ] **7.10** `App` TestFlight + Play internal testing

### 7.01 — POST /sync batch: per-item result, client-timestamp order, idempotent

_Backend_

**What**

One endpoint that accepts a batch of queued writes and returns a result for each one.

**Why**

After a day offline the outbox may hold 100 items. One request instead of 100 is faster and kinder to battery.

**Rules**

Process items in client-timestamp order. Each item is idempotent on its client_uuid (same rules as 2.06). One bad item doesn't fail the batch — return per-item status (ok, duplicate, rejected + code). Recalculate affected verdicts once at the end, not per item.

**Done when**

A batch containing duplicates and one invalid item applies the valid ones and reports each.

### 7.02 — Outbox switches to /sync batches; one-day airplane-mode soak test

_App_

**What**

The outbox worker sends up to ~50 items per /sync call and handles per-item results.

**Soak test**

Use the app normally with the phone in airplane mode for a whole day — training, meals, sleep, verdict actions — then reconnect. Check every item arrives once, in order, and the verdict is correct.

**Done when**

The soak test passes on both platforms with no duplicates.

### 7.03 — Bucket4j rate limits: per user, and per IP on /auth

_Backend_

**What**

Bucket4j limits how many requests a caller can make per time window.

**Limits**

Per user on normal endpoints (e.g. 120/min) to absorb buggy retry loops. Per IP on /auth/** (e.g. 10/min) because those are unauthenticated and attractive to attackers. Over the limit → 429 with a Retry-After header.

**Storage**

With one server, in-memory buckets are fine. With several, back them with Postgres (bucket4j-postgresql) so limits are shared. That's the only point Redis would become worth considering.

**Done when**

Exceeding each limit returns 429 and the app backs off.

### 7.04 — Micrometer + Prometheus; JSON logs with request id and no PII

_Backend_

**Metrics**

Micrometer (built into Actuator) records request counts, latencies, errors, DB pool usage. Expose /actuator/prometheus and point the host's monitoring or Grafana Cloud at it. Add custom counters: verdicts issued, vision calls, sync batch sizes.

**Logs**

JSON logs (one object per line) so the log platform can search fields. A filter adds a request id to every line and the response header, so one request can be traced end to end.

**No PII**

Never log emails, names, tokens, food descriptions or health values. Log IDs only.

**Done when**

You can graph p95 latency and find all log lines for one request.

### 7.05 — DELETE /me: cascade delete + revoke tokens; test asserts zero rows remain

_Backend_

**What**

Account deletion — required by both app stores and by GDPR.

**How**

Delete the users row; ON DELETE CASCADE in the schema removes every dependent row. Revoke all refresh tokens first. The app then wipes SQLite, SecureStore and thumbnails.

**Test**

Create a user with data in every table, delete, then assert every table has zero rows for that user_id. Rerun this test whenever you add a table.

**Done when**

The test passes and the app has a visible "Delete account" path.

### 7.06 — Prod Postgres: daily backups + PITR; restore drill into staging

_Database_

**What**

Production database protection.

**Terms**

Daily backup — a full snapshot each day.  
PITR (point-in-time recovery) — the provider also keeps the write-ahead log, so you can restore to any minute, e.g. just before a bad migration.

**Restore drill**

A backup you've never restored is a hope, not a backup. Restore prod into staging once, start the app against it, check data.

**Done when**

You've done a restore and written down the steps and how long it took.

### 7.07 — Prod deploy; Flyway migrates on startup; rollback = forward-fix migration

_Infra_

**What**

Production deployment of the same image that ran on staging, promoted by tag.

**Migrations on startup**

Flyway runs before the app accepts traffic. Write migrations expand-then-contract: add new columns in one release, switch code, remove old columns in a later release. Old and new code can then run side by side during a deploy.

**Rollback**

Don't write "down" migrations. If a migration is wrong, ship a new one that fixes it (forward fix). App code can roll back to the previous image because migrations stay backward compatible.

**Done when**

A merge to main deploys to staging; a manual approval promotes it to prod.

### 7.08 — Notifications and timezone-change handling

_App_

**Notifications**

expo-notifications for local reminders (e.g. log sleep, verdict ready). Ask permission at a moment the user sees the benefit, not on first launch.

**Timezone change**

When the device timezone differs from the profile's (travel), ask whether to update. Days are computed in the profile timezone, so changing it shifts what "today" means. Test a flight across several zones.

**Done when**

Reminders fire at the right local time after a timezone change.

### 7.09 — Store listings, privacy labels, health-data disclosures

_App_

**What**

Everything the stores need before testers can install.

**Apple**

App Store Connect listing, screenshots, privacy "nutrition label" (what data you collect and why), HealthKit usage justification, Sign in with Apple configured.

**Google**

Play Console listing, Data safety form, Health Connect permission declaration (reviewed separately and can take weeks — submit early, as the Progress milestone says).

**Done when**

Both stores have approved the builds for testing.

### 7.10 — TestFlight + Play internal testing

_App_

**What**

Distribute to beta testers. TestFlight on iOS (up to 10,000 external testers via a public link after a short beta review). Play internal testing on Android (up to 100 testers by email, no review delay).

**Detail**

Use the production EAS profile pointed at production. Prepare a short feedback channel and watch crash reports and metrics daily for the first week.

**Done when**

Testers outside the team have installed and logged their first workout.
