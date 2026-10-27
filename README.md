# Calozone

Training, sleep and food in one daily verdict: how hard to train today and how much to eat.

## Layout

| Folder   | What it holds |
|----------|---------------|
| `api/`   | Spring Boot 3 backend (Java 21, Gradle), PostgreSQL 16 via Flyway |
| `app/`   | Expo (React Native, TypeScript) mobile app for iOS and Android |
| `infra/` | Local docker-compose, deployment config, CI helpers |

Each folder has its own README with setup steps.

## Getting started

Requirements: Git, Docker, JDK 21, Node 20 LTS.

```bash
git clone <repo-url> calozone
cd calozone
docker compose -f infra/docker-compose.yml up -d   # Postgres on localhost:5432
cd api && ./gradlew bootRun                        # API on localhost:8080
cd ../app && npm install && npx expo start         # app dev server
```

## Working rules

- `main` is always deployable. Never push to it directly.
- Every change goes through a pull request and merges only when CI is green.
- Database changes are new Flyway migrations. Never edit one that has already run.
- Secrets come from environment variables. Never commit them; copy `.env.example` to `.env` locally.
